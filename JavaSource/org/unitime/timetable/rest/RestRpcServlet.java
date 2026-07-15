/*
 * Angular migration - Wave 0 facade prototype.
 *
 * Additive JSON facade over the existing GWT-RPC command beans. It changes
 * NO existing file and NO business logic: it deserializes a JSON body into the
 * concrete GwtRpcRequest POJO, hands it to the unchanged
 * GwtRpcServlet.execute(...) dispatcher, and serializes the GwtRpcResponse back
 * to JSON. All 152 @GwtRpcImplements command beans become reachable from
 * Angular through one endpoint.
 *
 *   POST /api/rpc/{RequestName}     body = JSON of the request POJO
 *
 * {RequestName} is the request class simple name (e.g. "PageNameRpcRequest")
 * or its fully-qualified name when a simple name is ambiguous.
 */
package org.unitime.timetable.rest;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.unitime.commons.hibernate.util.HibernateUtil;
import org.unitime.localization.impl.Localization;
import org.unitime.timetable.ApplicationProperties;
import org.unitime.timetable.gwt.command.client.GwtRpcRequest;
import org.unitime.timetable.gwt.command.client.GwtRpcResponse;
import org.unitime.timetable.gwt.command.server.GwtRpcHelper;
import org.unitime.timetable.gwt.command.server.GwtRpcServlet;
import org.unitime.timetable.gwt.shared.PageAccessException;
import org.unitime.timetable.rest.AsyncRpcExecutor.Status;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.context.AnonymousUserContext;
import org.unitime.timetable.security.context.HttpSessionContext;
import org.unitime.timetable.security.evaluation.PermissionCheck;
import org.unitime.timetable.util.Formats;

import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class RestRpcServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static Log sLog = LogFactory.getLog(RestRpcServlet.class);

	/** how long a finished async execution is retained for polling (30 min) */
	private static final long ASYNC_RETENTION_MS = 30 * 60 * 1000L;
	/** URL segment that marks the async lifecycle: /api/rpc/async/... */
	private static final String ASYNC = "async";

	/** simpleName / FQN -> concrete GwtRpcRequest class */
	private final Map<String, Class<? extends GwtRpcRequest<?>>> iRegistry = new HashMap<String, Class<? extends GwtRpcRequest<?>>>();
	private Gson iGson;
	private AsyncRpcExecutor iAsync;

	protected WebApplicationContext getApplicationContext() {
		return WebApplicationContextUtils.getWebApplicationContext(getServletContext());
	}

	protected SessionContext getSessionContext() {
		return HttpSessionContext.getSessionContext(getServletContext());
	}

	protected PermissionCheck getPermissionCheck() {
		return (PermissionCheck) getApplicationContext().getBean("unitimePermissionCheck");
	}

	@Override
	public void init() throws ServletException {
		iGson = createGson();
		iAsync = new AsyncRpcExecutor(ASYNC_RETENTION_MS);
		buildRegistry();
		sLog.info("RestRpcServlet ready: " + iRegistry.size() + " request name(s) mapped.");
	}

	/**
	 * Reuses the EXACT serialization contract from GwtRpcServlet: iField -> field
	 * naming and ISO date formatting, so JSON matches what the generated
	 * TypeScript models expect.
	 */
	public static Gson createGson() {
		return new GsonBuilder()
			.registerTypeAdapter(java.sql.Timestamp.class, new JsonSerializer<java.sql.Timestamp>() {
				public JsonElement serialize(java.sql.Timestamp src, Type t, JsonSerializationContext c) {
					return new JsonPrimitive(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(src));
				}
			})
			.registerTypeAdapter(java.sql.Date.class, new JsonSerializer<java.sql.Date>() {
				public JsonElement serialize(java.sql.Date src, Type t, JsonSerializationContext c) {
					return new JsonPrimitive(new SimpleDateFormat("yyyy-MM-dd").format(src));
				}
			})
			.registerTypeAdapter(Date.class, new JsonSerializer<Date>() {
				public JsonElement serialize(Date src, Type t, JsonSerializationContext c) {
					return new JsonPrimitive(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(src));
				}
			})
			.setFieldNamingStrategy(new FieldNamingStrategy() {
				Pattern iPattern = Pattern.compile("i([A-Z])(.*)");
				public String translateName(Field f) {
					Matcher m = iPattern.matcher(f.getName());
					return m.matches() ? m.group(1).toLowerCase() + m.group(2) : f.getName();
				}
			})
			// Abstract DTO hierarchies (e.g. ReservationInterface) get an "@type"
			// discriminator so they serialize/deserialize polymorphically.
			.registerTypeAdapterFactory(new PolymorphicTypeAdapterFactory(
					"org.unitime.timetable.gwt.shared", "org.unitime.timetable.gwt.client"))
			.create();
	}

	/**
	 * The command beans are registered by the request class FQN (see
	 * CustomBeanNameGenerator). We walk the bean names, resolve those that are
	 * GwtRpcRequest classes, and index them by simple name and FQN.
	 */
	@SuppressWarnings("unchecked")
	private void buildRegistry() {
		WebApplicationContext ctx = getApplicationContext();
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		Set<String> ambiguous = new HashSet<String>();
		for (String beanName : ctx.getBeanDefinitionNames()) {
			if (beanName.indexOf('.') < 0) continue; // not a class-name-keyed bean
			try {
				Class<?> c = Class.forName(beanName, false, cl);
				if (!GwtRpcRequest.class.isAssignableFrom(c)) continue;
				Class<? extends GwtRpcRequest<?>> rc = (Class<? extends GwtRpcRequest<?>>) c;
				iRegistry.put(c.getName(), rc);              // FQN always unique
				String simple = c.getSimpleName();
				if (iRegistry.containsKey(simple) && iRegistry.get(simple) != rc)
					ambiguous.add(simple);
				else
					iRegistry.put(simple, rc);
			} catch (Throwable ignore) { /* not a resolvable request class */ }
		}
		for (String s : ambiguous) {
			iRegistry.remove(s); // force callers to use FQN for collisions
			sLog.warn("Ambiguous request name '" + s + "' - callers must use the fully-qualified name.");
		}
	}

	// ---- path helpers ---------------------------------------------------------
	/** Path segments after /api/rpc, e.g. ["async","PageNameRpcRequest"] or ["async","42"] or ["PageNameRpcRequest"]. */
	private String[] segments(HttpServletRequest request) {
		String p = request.getPathInfo();
		if (p == null || p.equals("/")) return new String[0];
		if (p.startsWith("/")) p = p.substring(1);
		return p.split("/");
	}

	/**
	 * POST /api/rpc/{RequestName}          -> synchronous execute
	 * POST /api/rpc/async/{RequestName}    -> submit async, returns { executionId }
	 */
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String[] seg = segments(request);
		try {
			if (seg.length >= 1 && ASYNC.equals(seg[0])) {
				String name = (seg.length >= 2 ? seg[1] : null);
				submitAsync(name, request, response);
			} else {
				String name = (seg.length >= 1 ? seg[0] : null);
				executeSync(name, request, response);
			}
		} catch (Throwable t) {
			sendError(request, response, t);
		}
	}

	/**
	 * GET    /api/rpc/async/{executionId}  -> non-blocking status (202 running / 200 done|error|cancelled / 404 unknown)
	 */
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String[] seg = segments(request);
		try {
			if (seg.length == 2 && ASYNC.equals(seg[0]))
				pollAsync(seg[1], response);
			else
				throw new IllegalArgumentException("Use GET /api/rpc/async/{executionId}.");
		} catch (Throwable t) {
			sendError(request, response, t);
		}
	}

	/**
	 * DELETE /api/rpc/async/{executionId}  -> request cancellation
	 */
	@Override
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String[] seg = segments(request);
		try {
			if (seg.length == 2 && ASYNC.equals(seg[0]))
				cancelAsync(seg[1], response);
			else
				throw new IllegalArgumentException("Use DELETE /api/rpc/async/{executionId}.");
		} catch (Throwable t) {
			sendError(request, response, t);
		}
	}

	// ---- sync -----------------------------------------------------------------
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void executeSync(String name, HttpServletRequest request, HttpServletResponse response) throws IOException {
		GwtRpcRequest rpcRequest = deserialize(name, request);
		// Dispatch through the UNCHANGED command dispatcher and business bean.
		GwtRpcResponse result = GwtRpcServlet.execute(rpcRequest, getApplicationContext(), getSessionContext());
		writeJson(response, HttpServletResponse.SC_OK, result);
	}

	// ---- async ----------------------------------------------------------------
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void submitAsync(String name, HttpServletRequest request, HttpServletResponse response) throws IOException {
		final GwtRpcRequest rpcRequest = deserialize(name, request);
		final WebApplicationContext ctx = getApplicationContext();
		// Detach the session from the HTTP request: the background thread outlives it.
		final SessionContext detached = new GwtRpcHelper(getSessionContext(), getPermissionCheck());
		// Capture thread-locals on the request thread; replay them on the worker (mirrors GwtRpcServlet.Execution).
		final String locale = Localization.getLocale();
		final Long sessionId = (detached.getUser() == null ? null : detached.getUser().getCurrentAcademicSessionId());

		Callable<GwtRpcResponse> task = new Callable<GwtRpcResponse>() {
			public GwtRpcResponse call() {
				Localization.setLocale(locale);
				ApplicationProperties.setSessionId(sessionId);
				try {
					return GwtRpcServlet.execute(rpcRequest, ctx, detached);
				} finally {
					Localization.removeLocale();
					Formats.removeFormats();
					ApplicationProperties.setSessionId(null);
					HibernateUtil.closeCurrentThreadSessions();
				}
			}
		};

		long id = iAsync.submit(task);
		Map<String, Object> out = new LinkedHashMap<String, Object>();
		out.put("executionId", Long.toString(id));
		writeJson(response, HttpServletResponse.SC_ACCEPTED, out);
	}

	private void pollAsync(String idStr, HttpServletResponse response) throws IOException {
		Status status = iAsync.poll(parseId(idStr));
		if (status == null) { // unknown or expired
			writeJson(response, HttpServletResponse.SC_NOT_FOUND, errorBody("No execution '" + idStr + "' (unknown or expired)."));
			return;
		}
		Map<String, Object> out = new LinkedHashMap<String, Object>();
		out.put("status", status.getState().name());
		int code;
		switch (status.getState()) {
			case RUNNING:   code = HttpServletResponse.SC_ACCEPTED; break;
			case DONE:      code = HttpServletResponse.SC_OK; out.put("result", status.getResult()); break;
			case ERROR:     code = HttpServletResponse.SC_OK; out.put("error", status.getError()); break;
			case CANCELLED: code = HttpServletResponse.SC_OK; break;
			default:        code = HttpServletResponse.SC_OK;
		}
		writeJson(response, code, out);
	}

	private void cancelAsync(String idStr, HttpServletResponse response) throws IOException {
		boolean ok = iAsync.cancel(parseId(idStr));
		if (!ok) {
			writeJson(response, HttpServletResponse.SC_NOT_FOUND, errorBody("No execution '" + idStr + "' (unknown or expired)."));
			return;
		}
		Map<String, Object> out = new LinkedHashMap<String, Object>();
		out.put("status", AsyncRpcExecutor.State.CANCELLED.name());
		writeJson(response, HttpServletResponse.SC_OK, out);
	}

	private long parseId(String idStr) {
		try { return Long.parseLong(idStr); }
		catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid execution id '" + idStr + "'."); }
	}

	// ---- shared plumbing ------------------------------------------------------
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private GwtRpcRequest deserialize(String name, HttpServletRequest request) throws IOException {
		Class<? extends GwtRpcRequest<?>> reqClass = (name == null ? null : iRegistry.get(name));
		if (reqClass == null)
			throw new IllegalArgumentException("Unknown RPC request '" + name + "'.");
		String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		if (body.trim().isEmpty()) body = "{}";
		return iGson.fromJson(body, reqClass);
	}

	private void writeJson(HttpServletResponse response, int code, Object payload) throws IOException {
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		response.setStatus(code);
		response.getWriter().write(iGson.toJson(payload));
	}

	private Map<String, Object> errorBody(String message) {
		Map<String, Object> out = new LinkedHashMap<String, Object>();
		out.put("error", message);
		return out;
	}

	/** Error mapping mirrors ApiServlet.checkError. */
	private void sendError(HttpServletRequest request, HttpServletResponse response, Throwable t) throws IOException {
		int code;
		if (t instanceof IllegalArgumentException) {
			code = HttpServletResponse.SC_BAD_REQUEST;
			sLog.info(t.getMessage());
		} else if (t instanceof PageAccessException || t instanceof AccessDeniedException
				|| (t.getMessage() != null && t.getMessage().startsWith("Access denied"))) {
			// GwtRpcServlet.execute wraps AccessDeniedException into GwtRpcException,
			// so also treat an "Access denied" message as a permission failure.
			SessionContext cx = getSessionContext();
			boolean anon = !cx.isAuthenticated() || cx.getUser() instanceof AnonymousUserContext;
			if (anon) { response.setHeader("WWW-Authenticate", "Basic"); code = HttpServletResponse.SC_UNAUTHORIZED; }
			else code = HttpServletResponse.SC_FORBIDDEN;
			sLog.info(t.getMessage());
		} else {
			code = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
			sLog.warn(t.getMessage(), t);
		}
		response.setContentType("application/json");
		response.setStatus(code);
		String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
		response.getWriter().write("{\"error\":" + iGson.toJson(msg) + "}");
	}
}