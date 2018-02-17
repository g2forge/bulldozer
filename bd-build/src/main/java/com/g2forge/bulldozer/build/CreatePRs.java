package com.g2forge.bulldozer.build;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.RefSpec;
import org.slf4j.event.Level;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.g2forge.alexandria.data.graph.HGraph;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.alexandria.wizard.CommandLineStringInput;
import com.g2forge.alexandria.wizard.PropertyStringInput;
import com.g2forge.alexandria.wizard.UserPasswordInput;
import com.g2forge.alexandria.wizard.UserStringInput;
import com.g2forge.bulldozer.build.model.BulldozerProject;
import com.g2forge.bulldozer.build.model.Context;
import com.g2forge.gearbox.git.HGit;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class CreatePRs {
	public static void main(String[] args) throws JsonParseException, JsonMappingException, IOException, GitAPIException {
		final String branch = new CommandLineStringInput(args, 1).fallback(new UserStringInput("Branch", true)).get();
		final CreatePRs createPRs = new CreatePRs(new Context<BulldozerProject>(BulldozerProject::new, Paths.get(args[0])), branch);
		createPRs.createPRs();
	}

	protected final Context<BulldozerProject> context;

	protected final String branch;

	public void createPRs() throws GitAPIException, IOException {
		HLog.getLogControl().setLogLevel(Level.INFO);
		// Fail if any repositories are dirty
		getContext().failIfDirty();

		// Find the projects which have the relevant branch
		final List<String> projects = getContext().getProjects().values().stream().filter(p -> HGit.isBranch(p.getGit(), getBranch())).map(BulldozerProject::getName).collect(Collectors.toList());
		// Topologically sort the projects
		final List<String> order = HGraph.toposort(projects, p -> HCollection.intersection(getContext().getNameToProject().get(p).getDependencies().getTransitive().keySet(), projects), false);

		final TransportConfigCallback transportConfig;
		{
			final String key = new PropertyStringInput("ssh.key.file").fallback(new UserPasswordInput("SSH Key File")).get();
			final String passphrase = new PropertyStringInput("ssh.key.passphrase").fallback(new UserPasswordInput(String.format("SSH Passphrase for %1$s", key))).get();
			transportConfig = HGit.createTransportConfig(key, passphrase);
		}

		// In topological order...
		for (String name : order) {
			final BulldozerProject project = getContext().getProjects().get(name);

			// Push the branch
			final String remote = HGit.getMyRemote(project.getGit());
			log.info(String.format("Pushing %1$s to remote %3$s:%2$s", getBranch(), remote, project.getName()));
			project.getGit().push().setTransportConfigCallback(transportConfig).setRemote(remote).setRefSpecs(new RefSpec(getBranch() + ":" + getBranch())).call();
			HGit.setTracking(project.getGit(), getBranch(), remote, null);
			log.info(String.format("Successfully pushed %1$s", project.getName()));

			// Open the PR
			// Set the assignee, master label & milestone
		}
	}
}
