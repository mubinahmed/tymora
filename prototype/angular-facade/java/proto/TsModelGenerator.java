package proto;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.unitime.timetable.gwt.command.client.GwtRpcRequest;
import org.unitime.timetable.gwt.command.client.GwtRpcResponse;

/**
 * Generates TypeScript interfaces from the UniTime GWT shared DTOs by reflecting
 * over the COMPILED classes, so the output matches exactly what the facade's
 * Gson emits: iField -> field naming, enums as string unions, List -> [],
 * Map -> Record<string, V>, inheritance as `extends`.
 *
 * Seeds = every class in org.unitime.timetable.gwt.shared that implements
 * GwtRpcRequest or GwtRpcResponse; then a BFS pulls in every referenced DTO.
 *
 * Usage: TsModelGenerator <classesRoot> <outputFile>
 */
public class TsModelGenerator {
	static final Pattern FIELD = Pattern.compile("i([A-Z])(.*)");

	final ClassLoader cl = Thread.currentThread().getContextClassLoader();
	final Deque<Class<?>> queue = new ArrayDeque<Class<?>>();
	final Set<Class<?>> queued = new HashSet<Class<?>>();
	final Map<Class<?>, String> tsNames = new LinkedHashMap<Class<?>, String>();
	final Set<String> usedNames = new HashSet<String>();
	final Map<String, String> interfaces = new TreeMap<String, String>(); // tsName -> body
	final Map<String, String> enums = new TreeMap<String, String>();       // tsName -> body
	final Set<String> warnings = new HashSet<String>();

	public static void main(String[] args) throws Exception {
		String classesRoot = args.length > 0 ? args[0]
			: "../../target/unitime-4.8/WEB-INF/classes";
		String output = args.length > 1 ? args[1]
			: "../../ui/src/app/core/generated/models.generated.ts";

		TsModelGenerator gen = new TsModelGenerator();
		List<Class<?>> seeds = gen.scanSeeds(Paths.get(classesRoot));
		System.out.println("Seeds (GwtRpcRequest/Response in gwt.shared): " + seeds.size());
		for (Class<?> c : seeds) {
			gen.enqueue(c);
			gen.seedResponseType(c); // capture the T in GwtRpcRequest<T> (e.g. GwtRpcResponseList<MenuInterface>)
		}
		// Classic RemoteServices (Curricula/Reservation/Sectioning/Snapshot): pull in
		// their method param + return DTOs, which no command DTO references.
		gen.seedRemoteServices(Paths.get(classesRoot));
		gen.processQueue();
		gen.write(Paths.get(output));

		System.out.println("Generated " + gen.interfaces.size() + " interfaces, "
			+ gen.enums.size() + " enums -> " + output);
		if (!gen.warnings.isEmpty()) {
			System.out.println("Unmapped types (emitted as `any`):");
			gen.warnings.stream().sorted().forEach(w -> System.out.println("  - " + w));
		}
	}

	/**
	 * Keep every GwtRpcRequest/Response class under gwt.shared AND gwt.client:
	 * some request DTOs are declared as nested classes of client page widgets
	 * (e.g. DepartmentsEdit.UpdateDepartmentRequest), not in gwt.shared.
	 */
	private Path iClassesRoot;
	List<Class<?>> scanSeeds(Path classesRoot) throws IOException {
		iClassesRoot = classesRoot;
		List<Class<?>> seeds = new java.util.ArrayList<Class<?>>();
		for (String pkg : new String[] { "org/unitime/timetable/gwt/shared", "org/unitime/timetable/gwt/client" }) {
			Path dir = classesRoot.resolve(pkg);
			if (!Files.isDirectory(dir)) continue;
			try (Stream<Path> paths = Files.walk(dir)) {
				paths
					.filter(p -> p.toString().endsWith(".class"))
					.map(p -> classesRoot.relativize(p).toString())
					.map(s -> s.replace('\\', '/').replace('/', '.').replaceAll("\\.class$", ""))
					.map(this::loadOrNull)
					.filter(c -> c != null && isDto(c) && !c.isInterface() && !c.isEnum())
					.forEach(seeds::add);
			}
		}
		return seeds;
	}

	Class<?> loadOrNull(String name) {
		try { return Class.forName(name, false, cl); } catch (Throwable t) { return null; }
	}

	boolean isDto(Class<?> c) {
		return GwtRpcRequest.class.isAssignableFrom(c) || GwtRpcResponse.class.isAssignableFrom(c);
	}

	/** Enqueue param + return DTOs of every classic RemoteService in gwt.services. */
	void seedRemoteServices(Path classesRoot) throws IOException {
		Path dir = classesRoot.resolve("org/unitime/timetable/gwt/services");
		if (!Files.isDirectory(dir)) return;
		Class<?> remoteService = loadOrNull("com.google.gwt.user.client.rpc.RemoteService");
		if (remoteService == null) return;
		try (Stream<Path> paths = Files.walk(dir)) {
			paths
				.filter(p -> p.toString().endsWith(".class"))
				.map(p -> classesRoot.relativize(p).toString())
				.map(s -> s.replace('\\', '/').replace('/', '.').replaceAll("\\.class$", ""))
				.map(this::loadOrNull)
				.filter(c -> c != null && c.isInterface() && c != remoteService && remoteService.isAssignableFrom(c))
				.forEach(svc -> {
					for (java.lang.reflect.Method m : svc.getMethods()) {
						enqueueType(m.getGenericReturnType());
						for (Type p : m.getGenericParameterTypes()) enqueueType(p);
					}
				});
		}
	}

	/** Enqueue the response type declared in GwtRpcRequest&lt;T&gt; (walking up supertypes). */
	void seedResponseType(Class<?> c) {
		for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
			for (Type gi : k.getGenericInterfaces()) {
				if (gi instanceof ParameterizedType) {
					ParameterizedType pt = (ParameterizedType) gi;
					if (pt.getRawType() == GwtRpcRequest.class)
						enqueueType(pt.getActualTypeArguments()[0]);
				}
			}
		}
	}

	/** Enqueue a type, unwrapping List/Collection element types. */
	void enqueueType(Type t) {
		if (t instanceof Class) {
			enqueue((Class<?>) t);
		} else if (t instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) t;
			Class<?> raw = (Class<?>) pt.getRawType();
			if (Collection.class.isAssignableFrom(raw)) enqueueType(pt.getActualTypeArguments()[0]);
			else enqueue(raw);
		}
	}

	void enqueue(Class<?> c) {
		if (c == null || queued.contains(c)) return;
		if (!c.getName().startsWith("org.unitime")) return;
		if (Collection.class.isAssignableFrom(c) || Map.class.isAssignableFrom(c)) return;
		if (c.isEnum() || c.isInterface() || c.isPrimitive() || c.isArray()) return;
		queued.add(c);
		queue.add(c);
	}

	void processQueue() {
		while (!queue.isEmpty()) generate(queue.poll());
	}

	void generate(Class<?> c) {
		String name = tsName(c);
		StringBuilder sb = new StringBuilder();
		sb.append("/** ").append(c.getName().replace('$', '.')).append(" */\n");
		sb.append("export interface ").append(name);

		Class<?> sup = c.getSuperclass();
		boolean hasSuper = sup != null && sup != Object.class && sup.getName().startsWith("org.unitime")
			&& !Collection.class.isAssignableFrom(sup) && !Map.class.isAssignableFrom(sup);
		if (hasSuper) {
			enqueue(sup);
			sb.append(" extends ").append(tsName(sup));
		}
		sb.append(" {\n");

		// Abstract polymorphic base -> the facade fills in an @type discriminator
		// (subclass simple name). Declared on the base only; leaves inherit it, which
		// avoids literal-override conflicts across multi-level hierarchies.
		if (Modifier.isAbstract(c.getModifiers()) && !subtypesOf(c).isEmpty())
			sb.append("  '@type'?: string;\n");

		for (Field f : c.getDeclaredFields()) {
			int m = f.getModifiers();
			if (Modifier.isStatic(m) || Modifier.isTransient(m) || f.isSynthetic()) continue;
			sb.append("  ").append(jsonName(f.getName())).append("?: ")
				.append(tsType(f.getGenericType())).append(";\n");
		}
		sb.append("}\n");
		interfaces.put(name, sb.toString());

		// Pull concrete subtypes of an abstract base into the output.
		if (Modifier.isAbstract(c.getModifiers()))
			for (Class<?> sub : subtypesOf(c)) enqueue(sub);
	}

	/** Concrete subclasses of a base, from the scanned shared/client packages. */
	private List<Class<?>> subtypesOf(Class<?> base) {
		List<Class<?>> out = new java.util.ArrayList<Class<?>>();
		for (Class<?> c : allClasses())
			if (c != base && base.isAssignableFrom(c) && !c.isInterface() && !Modifier.isAbstract(c.getModifiers()))
				out.add(c);
		return out;
	}

	private List<Class<?>> iAllClasses;
	private List<Class<?>> allClasses() {
		if (iAllClasses != null) return iAllClasses;
		iAllClasses = new java.util.ArrayList<Class<?>>();
		for (String pkg : new String[] { "org/unitime/timetable/gwt/shared", "org/unitime/timetable/gwt/client" }) {
			Path dir = iClassesRoot.resolve(pkg);
			if (!Files.isDirectory(dir)) continue;
			try (Stream<Path> paths = Files.walk(dir)) {
				paths.filter(p -> p.toString().endsWith(".class"))
					.map(p -> iClassesRoot.relativize(p).toString().replace('\\', '/').replace('/', '.').replaceAll("\\.class$", ""))
					.map(this::loadOrNull)
					.filter(x -> x != null)
					.forEach(iAllClasses::add);
			} catch (IOException ignore) { }
		}
		return iAllClasses;
	}

	String jsonName(String field) {
		Matcher mt = FIELD.matcher(field);
		return mt.matches() ? mt.group(1).toLowerCase() + mt.group(2) : field;
	}

	String tsType(Type t) {
		if (t instanceof Class<?>) return tsClass((Class<?>) t);
		if (t instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) t;
			Class<?> raw = (Class<?>) pt.getRawType();
			Type[] args = pt.getActualTypeArguments();
			if (Collection.class.isAssignableFrom(raw)) return elementType(tsType(args[0])) + "[]";
			// Index signature (not Record<>) — a UniTime DTO is literally named "Record".
			if (Map.class.isAssignableFrom(raw)) return "{ [key: string]: " + tsType(args[args.length - 1]) + " }";
			return tsClass(raw);
		}
		if (t instanceof GenericArrayType)
			return elementType(tsType(((GenericArrayType) t).getGenericComponentType())) + "[]";
		if (t instanceof WildcardType) return "any";
		if (t instanceof TypeVariable) return "any";
		return "any";
	}

	/** Parenthesize union element types before appending []. */
	String elementType(String ts) {
		return ts.contains("|") ? "(" + ts + ")" : ts;
	}

	String tsClass(Class<?> c) {
		if (c == boolean.class || c == Boolean.class) return "boolean";
		if (c == char.class || c == Character.class || c == String.class || CharSequence.class.isAssignableFrom(c))
			return "string";
		if (c == byte.class || c == short.class || c == int.class || c == long.class || c == float.class
			|| c == double.class || Number.class.isAssignableFrom(c) || c == BigDecimal.class || c == BigInteger.class)
			return "number";
		if (java.util.Date.class.isAssignableFrom(c)) return "string"; // facade emits ISO strings
		if (c.isArray()) return elementType(tsClass(c.getComponentType())) + "[]";
		if (c.isEnum()) { registerEnum(c); return tsName(c); }
		if (Collection.class.isAssignableFrom(c)) return "any[]";  // raw collection
		if (Map.class.isAssignableFrom(c)) return "{ [key: string]: any }";
		if (c.getName().startsWith("org.unitime")) { enqueue(c); return tsName(c); }
		if (c == Object.class) return "any";
		warnings.add(c.getName());
		return "any";
	}

	void registerEnum(Class<?> c) {
		String name = tsName(c);
		if (enums.containsKey(name)) return;
		String union = Stream.of(c.getEnumConstants())
			.map(e -> "'" + ((Enum<?>) e).name() + "'")
			.collect(Collectors.joining(" | "));
		enums.put(name, "/** " + c.getName().replace('$', '.') + " */\nexport type " + name + " = " + union + ";\n");
	}

	/** Stable TS name; disambiguates simple-name collisions with the enclosing name. */
	String tsName(Class<?> c) {
		String existing = tsNames.get(c);
		if (existing != null) return existing;
		String simple = c.getSimpleName();
		String name = simple;
		if (usedNames.contains(name)) {
			Class<?> enc = c.getEnclosingClass();
			name = (enc != null ? enc.getSimpleName() + "_" : "") + simple;
			int n = 2;
			while (usedNames.contains(name)) name = simple + n++;
		}
		usedNames.add(name);
		tsNames.put(c, name);
		return name;
	}

	void write(Path out) throws IOException {
		Files.createDirectories(out.getParent());
		StringBuilder sb = new StringBuilder();
		sb.append("// AUTO-GENERATED from org.unitime.timetable.gwt.shared by TsModelGenerator.\n");
		sb.append("// Do not edit by hand. Field names follow the facade's Gson naming (iField -> field).\n\n");
		enums.values().forEach(sb::append);
		if (!enums.isEmpty()) sb.append("\n");
		interfaces.values().forEach(v -> sb.append(v).append("\n"));
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
			w.print(sb);
		}
	}
}
