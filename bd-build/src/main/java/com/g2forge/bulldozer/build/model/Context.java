package com.g2forge.bulldozer.build.model;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;

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
public class Context<P extends BulldozerProject> {
	protected final IFunction2<? super Context<P>, ? super MavenProject, ? extends P> constructor;

	@Getter(lazy = true)
	private final XmlMapper xmlMapper = new XmlMapper();

	@Getter(lazy = true)
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Getter(lazy = true)
	private final IMaven maven = new Proxifier().generate(new ProcessBuilderRunner(), IMaven.class);

	protected final Path root;

	@Getter(lazy = true)
	private final Map<String, P> projects = computeProjects();

	@Getter(lazy = true)
	private final Map<String, P> nameToProject = getProjects().values().stream().collect(Collectors.toMap(BulldozerProject::getName, IFunction1.identity()));

	@Getter(lazy = true)
	private final Map<String, P> groupToProject = getProjects().values().stream().collect(Collectors.toMap(BulldozerProject::getGroup, IFunction1.identity()));

	protected final Map<String, P> computeProjects() {
		final Map<String, P> retVal = new LinkedHashMap<>();
		for (MavenProject mavenProject : new MavenProjects(getRoot().resolve(IMaven.POM_XML)).getProjects()) {
			final P bulldozerProject = getConstructor().apply(this, mavenProject);
			retVal.put(bulldozerProject.getName(), bulldozerProject);
		}
		return retVal;
	}

	public void failIfDirty() {
		final List<BulldozerProject> dirty = getProjects().values().stream().filter(project -> {
			try {
				final Status status = project.getGit().status().call();
				return !status.isClean() && !status.getUncommittedChanges().isEmpty();
			} catch (NoWorkTreeException | GitAPIException e) {
				throw new RuntimeException(String.format("Failure while attempting to check whether %1$s is dirty!", project.getName()), e);
			}
		}).collect(Collectors.toList());
		if (!dirty.isEmpty()) throw new IllegalStateException(String.format("One or more projects were dirty (%1$s), please commit changes and try again!", dirty.stream().map(BulldozerProject::getName).collect(Collectors.joining(", "))));
	}
}
