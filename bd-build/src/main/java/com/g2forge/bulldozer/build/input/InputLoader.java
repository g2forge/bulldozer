package com.g2forge.bulldozer.build.input;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.g2forge.alexandria.java.fluent.optional.IOptional;
import com.g2forge.alexandria.java.function.IConsumer2;
import com.g2forge.alexandria.java.function.IPredicate1;
import com.g2forge.alexandria.java.io.HIO;
import com.g2forge.alexandria.java.io.RuntimeIOException;
import com.g2forge.alexandria.wizard.PropertyStringInput;
import com.g2forge.alexandria.wizard.UserStringInput;

public class InputLoader {
	protected IOptional<InputStream> getStream(InputType<?, ?> type) {
		return new PropertyStringInput("configfile." + type.getName().toLowerCase()).map(Paths::get).map(path -> {
			try {
				return Files.newInputStream(path);
			} catch (IOException e) {
				throw new RuntimeIOException(e);
			}
		});
	}

	protected <T, B, F> boolean isAcceptable(T loaded, InputField<T, B, F> field) {
		final F value = field.getGetter().apply(loaded);
		final IPredicate1<? super F> acceptable = field.getAcceptable();
		if (acceptable == null) return true;
		return acceptable.test(value);
	}

	public <T, B> T load(InputType<T, B> type) {
		final B builder;
		final Collection<InputField<T, B, ?>> load;
		// If there's a config file property, then read the input using that & a specified mapper
		final IOptional<InputStream> stream = getStream(type);
		if (!stream.isEmpty()) {
			final T loaded;
			final InputStream actualStream = stream.get();
			try {
				loaded = type.getLoader().apply(actualStream);
			} finally {
				HIO.close(actualStream);
			}

			// Find all the fields we don't have acceptable values for, and use a property or a user input
			final Map<Boolean, List<InputField<T, B, ?>>> grouped = type.getFields().stream().collect(Collectors.groupingBy(field -> isAcceptable(loaded, field), Collectors.toList()));
			load = grouped.get(false);
			if ((load == null) || load.isEmpty()) return loaded;

			builder = type.getFactory().get();
			for (InputField<T, B, ?> field : grouped.get(true)) {
				transfer(loaded, field, builder);
			}
		} else {
			load = type.getFields();
			builder = type.getFactory().get();
		}

		// Load all the remaining fields
		for (InputField<T, B, ?> field : load) {
			load(type, field, builder);
		}

		return type.getBuilder().apply(builder);
	}

	protected <T, B, F> void load(InputType<T, B> type, InputField<T, B, F> field, B builder) {
		final IConsumer2<? super B, ? super F> setter = field.getSetter();

		final IOptional<F> override = field.getOverride();
		if ((override != null) && !override.isEmpty()) {
			setter.accept(builder, override.get());
		} else {
			final IOptional<String> optional = read(type, field);
			if (optional.isEmpty()) return;
			final F value = field.getConverter().apply(optional.get());
			setter.accept(builder, value);
		}
	}

	protected <T, B, F> IOptional<String> read(InputType<T, B> type, InputField<T, B, F> field) {
		return new PropertyStringInput(type.getName().toLowerCase() + "." + field.getName().toLowerCase()).fallback(new UserStringInput(type.getName() + " " + field.getName(), true));
	}

	protected <T, B, F> void transfer(T loaded, InputField<T, B, F> field, B builder) {
		final F value = field.getGetter().apply(loaded);
		field.getSetter().accept(builder, value);
	}
}
