package com.g2forge.bulldozer.build.model;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.alexandria.java.function.IFunction2;
import com.g2forge.alexandria.java.io.RuntimeIOException;
import com.g2forge.alexandria.java.project.HProject;
import com.g2forge.alexandria.wizard.PropertyStringInput;
import com.g2forge.alexandria.wizard.UserPasswordInput;
import com.g2forge.bulldozer.build.maven.IMaven;
import com.g2forge.bulldozer.build.model.maven.MavenProject;
import com.g2forge.bulldozer.build.model.maven.MavenProjects;
import com.g2forge.gearbox.command.converter.dumb.DumbCommandConverter;
import com.g2forge.gearbox.command.process.ProcessBuilderRunner;
import com.g2forge.gearbox.command.proxy.CommandProxyFactory;
import com.g2forge.gearbox.git.HGit;

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
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Getter(lazy = true)
	private final IMaven maven = new CommandProxyFactory(DumbCommandConverter.create(), new ProcessBuilderRunner()).apply(IMaven.class);

	@Getter(lazy = true)
	private final GitHub github = computeGitHub();

	@Getter(lazy = true)
	private final TransportConfigCallback transportConfig = computeTransportConfig();

	protected final Path root;

	@Getter(lazy = true)
	private final Map<String, P> projects = computeProjects();

	@Getter(lazy = true)
	private final String rootProject = HCollection.getLast(getProjects().keySet());

	@Getter(lazy = true)
	private final Map<String, P> nameToProject = getProjects().values().stream().collect(Collectors.toMap(BulldozerProject::getName, IFunction1.identity()));

	@Getter(lazy = true)
	private final Map<String, P> groupToProject = getProjects().values().stream().collect(Collectors.toMap(BulldozerProject::getGroup, IFunction1.identity()));

	protected GitHub computeGitHub() {
		final String user = new PropertyStringInput("github.user").fallback(new UserPasswordInput("GitHub Username")).get();
		final String token = new PropertyStringInput("github.token").fallback(new UserPasswordInput("GitHub OAuth Token")).get();
		try {
			return new GitHubBuilder().withOAuthToken(token, user).build();
		} catch (IOException e) {
			throw new RuntimeIOException(e);
		}
	}

	protected final Map<String, P> computeProjects() {
		final Map<String, P> retVal = new LinkedHashMap<>();
		for (MavenProject mavenProject : new MavenProjects(getRoot().resolve(HProject.POM)).getProjects()) {
			final P bulldozerProject = getConstructor().apply(this, mavenProject);
			retVal.put(bulldozerProject.getName(), bulldozerProject);
		}
		return retVal;
	}

	protected TransportConfigCallback computeTransportConfig() {
		final String key = new PropertyStringInput("ssh.key.file").fallback(new UserPasswordInput("SSH Key File")).get();
		final String passphrase = new PropertyStringInput("ssh.key.passphrase").fallback(new UserPasswordInput(String.format("SSH Passphrase for %1$s", key))).get();
		return HGit.createTransportConfig(key, passphrase);
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
