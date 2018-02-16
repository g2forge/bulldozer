package com.g2forge.bulldozer.build.model;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.alexandria.java.function.IFunction2;
import com.g2forge.bulldozer.build.maven.IMaven;
import com.g2forge.bulldozer.build.model.maven.MavenProject;
import com.g2forge.bulldozer.build.model.maven.MavenProjects;
import com.g2forge.gearbox.functional.proxy.Proxifier;
import com.g2forge.gearbox.functional.runner.ProcessBuilderRunner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@AllArgsConstructor
public class Context {
	@Getter(lazy = true)
	private final XmlMapper xmlMapper = new XmlMapper();

	@Getter(lazy = true)
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Getter(lazy = true)
	private final IMaven maven = new Proxifier().generate(new ProcessBuilderRunner(), IMaven.class);

	protected final Path root;

	public <P extends BulldozerProject> Map<String, P> loadProjects(IFunction2<? super Context, ? super MavenProject, ? extends P> constructor) {
		return new MavenProjects(getRoot().resolve(IMaven.POM_XML)).getProjects().stream().map(project -> constructor.apply(this, project)).collect(Collectors.toMap(BulldozerProject::getName, IFunction1.identity()));
	}
}
