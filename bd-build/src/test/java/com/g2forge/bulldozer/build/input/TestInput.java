package com.g2forge.bulldozer.build.input;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g2forge.alexandria.java.fluent.optional.IOptional;
import com.g2forge.alexandria.java.fluent.optional.NullableOptional;
import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.alexandria.java.io.RuntimeIOException;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Singular;

public class TestInput {
	@Data
	@Builder
	@AllArgsConstructor
	public static class Config {
		public static InputType<Config, Config.ConfigBuilder> createInputType(IOptional<String> field0Override, IOptional<Boolean> field1Override) {
			final InputType.InputTypeBuilder<Config, Config.ConfigBuilder> type = InputType.builder();
			type.name(Config.class.getSimpleName());
			type.factory(Config::builder).builder(Config.ConfigBuilder::build);
			type.loader(stream -> {
				try {
					return mapper.readValue(stream, Config.class);
				} catch (IOException e) {
					throw new RuntimeIOException(e);
				}
			});

			type.field(InputField.<Config, Config.ConfigBuilder, String>builder().name("Field0").getter(Config::getField0).setter(Config.ConfigBuilder::field0).converter(IFunction1.identity()).override(field0Override).build());
			type.field(InputField.<Config, Config.ConfigBuilder, Boolean>builder().name("Field1").getter(Config::getField1).setter(Config.ConfigBuilder::field1).acceptable(Objects::nonNull).converter(Boolean::valueOf).override(field1Override).build());
			return type.build();
		}

		protected final String field0;

		protected final Boolean field1;
	}

	@Data
	@Builder
	@AllArgsConstructor
	@EqualsAndHashCode(callSuper = false)
	protected static class TestConfigLoader extends InputLoader {
		protected final Config config;

		/**
		 * A map from field names to the resulting value the "user" has entered.
		 */
		@Singular
		protected final Map<String, String> fields;

		@Override
		protected IOptional<InputStream> getStream(InputType<?, ?> type) {
			if (getConfig() == null) return NullableOptional.empty();

			final ByteArrayOutputStream output = new ByteArrayOutputStream();
			try {
				mapper.writeValue(output, getConfig());
			} catch (IOException exception) {
				throw new RuntimeIOException(exception);
			}
			return NullableOptional.of(new ByteArrayInputStream(output.toByteArray()));
		}

		@Override
		protected <T, B, F> IOptional<String> read(InputType<T, B> type, InputField<T, B, F> field) {
			final String name = field.getName();
			if (!getFields().containsKey(name)) return NullableOptional.empty();
			return NullableOptional.of(getFields().get(name));
		}
	}

	protected static final ObjectMapper mapper = new ObjectMapper();

	@Test
	public void fileAll() {
		final Config config = new Config("value", true);
		final InputLoader loader = TestConfigLoader.builder().config(config).build();
		Assert.assertEquals(config, loader.load(Config.createInputType(null, null)));
	}

	@Test
	public void fileNone() {
		final InputLoader loader = TestConfigLoader.builder().field("Field0", "value").build();
		Assert.assertEquals(new Config("value", null), loader.load(Config.createInputType(null, null)));
	}

	/**
	 * Test a partial load from file. Verify that {@link InputField#acceptable} and {@link InputField#converter} work as expected.
	 */
	@Test
	public void filePartial() {
		final InputLoader loader = TestConfigLoader.builder().config(new Config("value", null)).field("Field1", "false").build();
		Assert.assertEquals(new Config("value", false), loader.load(Config.createInputType(null, null)));
	}

	/**
	 * Verify that if an empty override is specified for the fields, it has no effect.
	 */
	@Test
	public void overrideEmpty() {
		final InputLoader loader = TestConfigLoader.builder().field("Field0", "value").build();
		Assert.assertEquals(new Config("value", null), loader.load(Config.createInputType(NullableOptional.empty(), null)));
	}

	/**
	 * Verify that if an override values is specified, the user won't be prompted.
	 */
	@Test
	public void overrideFull() {
		final InputLoader loader = TestConfigLoader.builder().field("Field0", "value").build();
		Assert.assertEquals(new Config("override", null), loader.load(Config.createInputType(NullableOptional.of("override"), null)));
	}
}
