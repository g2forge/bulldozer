package com.g2forge.bulldozer.build.input;

import java.io.InputStream;
import java.util.Collection;

import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.alexandria.java.function.ISupplier;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
@AllArgsConstructor
public class InputType<T, B> {
	protected final String name;

	protected final IFunction1<? super InputStream, ? extends T> loader;

	protected final ISupplier<? extends B> factory;

	protected final IFunction1<? super B, ? extends T> builder;

	@Singular
	protected final Collection<InputField<T, B, ?>> fields;
}
