package com.g2forge.bulldozer.build.input;

import com.g2forge.alexandria.java.fluent.optional.IOptional;
import com.g2forge.alexandria.java.function.IConsumer2;
import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.alexandria.java.function.IPredicate1;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class InputField<T, B, F> {
	/**
	 * Name of this field.
	 */
	protected final String name;

	/**
	 * Optional which, if not empty, specifies the value for this field over everything else.
	 */
	protected final IOptional<F> override;

	/**
	 * Getter to access the value of this field in an object read from a file.
	 */
	protected final IFunction1<? super T, ? extends F> getter;

	/**
	 * Test to see if the value read from the config file is acceptable.
	 */
	protected final IPredicate1<? super F> acceptable;

	/**
	 * Converter from a string the user specified in the GUI, console or a property into the field value.
	 */
	protected final IFunction1<? super String, ? extends F> converter;

	/**
	 * Setter to set this field on the builder.
	 */
	protected final IConsumer2<? super B, ? super F> setter;
}
