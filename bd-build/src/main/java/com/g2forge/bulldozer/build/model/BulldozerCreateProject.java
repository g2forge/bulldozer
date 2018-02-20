package com.g2forge.bulldozer.build.model;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semver.Version;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.alexandria.java.io.RuntimeIOException;
import com.g2forge.bulldozer.build.input.InputField;
import com.g2forge.bulldozer.build.input.InputType;
import com.g2forge.bulldozer.build.model.maven.MavenProject;
import com.g2forge.bulldozer.build.semver.VersionDeserializer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
@AllArgsConstructor
public class BulldozerCreateProject {
	public static InputType<BulldozerCreateProject, BulldozerCreateProject.BulldozerCreateProjectBuilder> createInputType(Context<?> context) {
		final InputType.InputTypeBuilder<BulldozerCreateProject, BulldozerCreateProject.BulldozerCreateProjectBuilder> type = InputType.builder();
		type.name("Create");
		type.factory(BulldozerCreateProject::builder).builder(BulldozerCreateProject.BulldozerCreateProjectBuilder::build);
		type.loader(stream -> {
			try {
				return context.getObjectMapper().readValue(stream, BulldozerCreateProject.class);
			} catch (IOException e) {
				throw new RuntimeIOException(e);
			}
		});

		type.field(InputField.<BulldozerCreateProject, BulldozerCreateProject.BulldozerCreateProjectBuilder, String>builder().name("Name").getter(BulldozerCreateProject::getName).setter(BulldozerCreateProject.BulldozerCreateProjectBuilder::name).converter(IFunction1.identity()).acceptable(Objects::nonNull).build());
		type.field(InputField.<BulldozerCreateProject, BulldozerCreateProject.BulldozerCreateProjectBuilder, String>builder().name("Description").getter(BulldozerCreateProject::getDescription).setter(BulldozerCreateProject.BulldozerCreateProjectBuilder::description).converter(IFunction1.identity()).acceptable(Objects::nonNull).build());
		type.field(InputField.<BulldozerCreateProject, BulldozerCreateProject.BulldozerCreateProjectBuilder, String>builder().name("Prefix").getter(BulldozerCreateProject::getPrefix).setter(BulldozerCreateProject.BulldozerCreateProjectBuilder::prefix).converter(IFunction1.identity()).acceptable(Objects::nonNull).build());
		type.field(InputField.<BulldozerCreateProject, BulldozerCreateProject.BulldozerCreateProjectBuilder, Version>builder().name("Version").getter(BulldozerCreateProject::getVersion).setter(BulldozerCreateProject.BulldozerCreateProjectBuilder::version).converter(Version::parse).acceptable(Objects::nonNull).build());
		type.field(InputField.<BulldozerCreateProject, BulldozerCreateProject.BulldozerCreateProjectBuilder, MavenProject.Protection>builder().name("Protection").getter(BulldozerCreateProject::getProtection).setter(BulldozerCreateProject.BulldozerCreateProjectBuilder::protection).converter(MavenProject.Protection::valueOf).acceptable(Objects::nonNull).build());
		type.field(InputField.<BulldozerCreateProject, BulldozerCreateProject.BulldozerCreateProjectBuilder, List<String>>builder().name("Dependencies").getter(BulldozerCreateProject::getDependencies).setter(BulldozerCreateProject.BulldozerCreateProjectBuilder::dependencies).converter(string -> string == null ? HCollection.emptyList() : Stream.of(string.split(",")).map(String::trim).collect(Collectors.toList())).acceptable(Objects::nonNull).build());
		return type.build();
	}

	protected final String name;

	protected final String description;

	protected final String prefix;

	@JsonSerialize(using = ToStringSerializer.class)
	@JsonDeserialize(using = VersionDeserializer.class)
	protected final Version version;

	protected final MavenProject.Protection protection;

	@Singular
	protected final List<String> dependencies;
}
