/*
 * Angular migration - Wave 0 facade: classic GWT-RPC RemoteService bridge.
 *
 * The command pattern (GwtRpcServlet) covers most of UniTime, but four screens
 * use classic method-based RemoteServices (CurriculaService, ReservationService,
 * SectioningService, LimitAndProjectionSnapshotService), each a Spring bean named
 * by its @RemoteServiceRelativePath (e.g. "reservation.gwt") with an @Autowired,
 * request-aware SessionContext.
 *
 * This additive servlet exposes them as JSON without touching the services:
 *
 *   POST /api/service/{servicePath}/{method}   body = JSON array of arguments
 *
 * It resolves the bean, matches the method by name + arg count, deserializes each
 * argument into the method's declared parameter type, invokes it (the bean's own
 * SessionContext handles auth), and serializes the result. No GWT code changes.
 */
package org.unitime.timetable.rest;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.unitime.timetable.gwt.shared.PageAccessException;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.context.AnonymousUserContext;
import org.unitime.timetable.security.context.HttpSessionContext;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

public class RestServiceServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static Log sLog = LogFactory.getLog(RestServiceServlet.class);
	private Gson iGson;

	protected WebApplicationContext getApplicationContext() {
		return WebApplicationContextUtils.getWebApplicationContext(getServletContext());
	}

	protected SessionContext getSessionContext() {
		return HttpSessionContext.getSessionContext(getServletContext());
	}

	@Override
	public void init() throws ServletException {
		iGson = RestRpcServlet.createGson(); // same iField->field + ISO-date contract
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String path = request.getPathInfo();
		if (path != null && path.startsWith("/")) path = path.substring(1);
		String[] seg = (path == null || path.isEmpty()) ? new String[0] : path.split("/");
		try {
			if (seg.length != 2)
				throw new IllegalArgumentException("Use POST /api/service/{servicePath}/{method}.");
			String servicePath = seg[0], methodName = seg[1];

			Object bean = getApplicationContext().getBean(servicePath);

			String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			JsonArray argsJson = body.isEmpty() ? new JsonArray() : JsonParser.parseString(body).getAsJsonArray();

			Method method = findMethod(bean.getClass(), methodName, argsJson.size());
			Type[] paramTypes = method.getGenericParameterTypes();
			Object[] args = new Object[paramTypes.length];
			for (int i = 0; i < paramTypes.length; i++)
				args[i] = iGson.fromJson(argsJson.get(i), paramTypes[i]);

			Object result;
			try {
				result = method.invoke(bean, args);
			} catch (InvocationTargetException e) {
				throw (e.getCause() != null ? e.getCause() : e);
			}

			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write(iGson.toJson(result));
		} catch (Throwable t) {
			sendError(response, t);
		}
	}

	private Method findMethod(Class<?> type, String name, int argCount) {
		Method match = null;
		for (Method m : type.getMethods()) {
			if (m.getName().equals(name) && m.getParameterCount() == argCount) {
				if (match != null)
					throw new IllegalArgumentException("Ambiguous method '" + name + "' with " + argCount + " arg(s).");
				match = m;
			}
		}
		if (match == null)
			throw new IllegalArgumentException("No method '" + name + "' with " + argCount + " arg(s).");
		return match;
	}

	private void sendError(HttpServletResponse response, Throwable t) throws IOException {
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
