/*
 * Angular migration - Wave 0 facade: polymorphism support.
 *
 * Several Tymora DTO hierarchies are abstract with concrete subclasses
 * (e.g. ReservationInterface -> Course/Group/Individual/Override). Plain Gson
 * cannot deserialize JSON into an abstract type, and on serialize it emits no
 * hint of the runtime subtype. This TypeAdapterFactory fixes both:
 *
 *   - on write it prefixes an "@type" discriminator (the subclass simple name)
 *     then the subclass's fields (via the delegate adapter, so the iField->field
 *     naming still applies);
 *   - on read it reads "@type", picks the subclass, and delegates.
 *
 * It only engages for ABSTRACT org.Tymora classes that have concrete subtypes,
 * discovered once (cached) by classpath-scanning the shared/client packages, so
 * concrete DTOs are untouched. Additive; no existing class changes.
 */
package org.unitime.timetable.rest;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class PolymorphicTypeAdapterFactory implements TypeAdapterFactory {
	static final String TYPE = "@type";

	private final String[] iPackages;
	private final Map<Class<?>, List<Class<?>>> iSubtypes = new ConcurrentHashMap<Class<?>, List<Class<?>>>();

	public PolymorphicTypeAdapterFactory(String... packages) { iPackages = packages; }

	@Override
	public <T> TypeAdapter<T> create(final Gson gson, TypeToken<T> typeToken) {
		final Class<? super T> raw = typeToken.getRawType();
		if (raw == null || !raw.getName().startsWith("org.unitime")) return null;
		if (raw.isInterface() || raw.isEnum() || raw.isPrimitive() || raw.isArray()) return null;
		if (!Modifier.isAbstract(raw.getModifiers())) return null; // only abstract bases need this
		final List<Class<?>> subs = subtypesOf(raw);
		if (subs.isEmpty()) return null;

		final Map<String, TypeAdapter<?>> byLabel = new HashMap<String, TypeAdapter<?>>();
		final Map<Class<?>, TypeAdapter<?>> byClass = new HashMap<Class<?>, TypeAdapter<?>>();
		final Map<Class<?>, String> labelByClass = new HashMap<Class<?>, String>();
		for (Class<?> sub : subs) {
			try {
				// Building the delegate eagerly surfaces model quirks (e.g. a subclass
				// that shadows a superclass field -> Gson duplicate-field error); skip
				// such subtypes rather than break the whole hierarchy.
				TypeAdapter<?> delegate = gson.getDelegateAdapter(this, TypeToken.get(sub));
				byLabel.put(sub.getSimpleName(), delegate);
				byClass.put(sub, delegate);
				labelByClass.put(sub, sub.getSimpleName());
			} catch (Exception skip) { /* subtype not Gson-serializable; excluded */ }
		}
		if (byClass.isEmpty()) return null;
		final TypeAdapter<JsonElement> elementAdapter = gson.getAdapter(JsonElement.class);

		TypeAdapter<T> result = new TypeAdapter<T>() {
			@Override
			@SuppressWarnings("unchecked")
			public void write(JsonWriter out, T value) throws IOException {
				Class<?> vc = value.getClass();
				TypeAdapter<Object> delegate = (TypeAdapter<Object>) byClass.get(vc);
				String label = labelByClass.get(vc);
				if (delegate == null) { // runtime subtype not scanned - best effort
					delegate = (TypeAdapter<Object>) gson.getDelegateAdapter(PolymorphicTypeAdapterFactory.this, TypeToken.get(vc));
					label = vc.getSimpleName();
				}
				JsonObject body = delegate.toJsonTree(value).getAsJsonObject();
				JsonObject wrapped = new JsonObject();
				wrapped.addProperty(TYPE, label);
				for (Map.Entry<String, JsonElement> e : body.entrySet()) wrapped.add(e.getKey(), e.getValue());
				elementAdapter.write(out, wrapped);
			}

			@Override
			@SuppressWarnings("unchecked")
			public T read(JsonReader in) throws IOException {
				JsonElement root = elementAdapter.read(in);
				if (root == null || root.isJsonNull()) return null;
				JsonObject obj = root.getAsJsonObject();
				JsonElement typeEl = obj.remove(TYPE);
				String label = (typeEl != null && !typeEl.isJsonNull()) ? typeEl.getAsString() : null;
				TypeAdapter<?> delegate = (label != null) ? byLabel.get(label) : null;
				if (delegate == null) {
					if (subs.size() == 1) delegate = byClass.get(subs.get(0)); // unambiguous
					else throw new JsonParseException("Missing/unknown " + TYPE + " for " + raw.getName());
				}
				return (T) delegate.fromJsonTree(obj);
			}
		};
		return result.nullSafe();
	}

	/** Concrete subclasses of an abstract base, scanned once per base. */
	private List<Class<?>> subtypesOf(final Class<?> base) {
		List<Class<?>> cached = iSubtypes.get(base);
		if (cached != null) return cached;
		List<Class<?>> found = new ArrayList<Class<?>>();
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AssignableTypeFilter(base));
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		for (String pkg : iPackages) {
			for (BeanDefinition bd : scanner.findCandidateComponents(pkg)) {
				try {
					Class<?> c = Class.forName(bd.getBeanClassName(), false, cl);
					if (c != base && base.isAssignableFrom(c) && !c.isInterface() && !Modifier.isAbstract(c.getModifiers()))
						found.add(c);
				} catch (Throwable ignore) { /* skip unresolvable */ }
			}
		}
		iSubtypes.put(base, found);
		return found;
	}
}
