package com.g2forge.bulldozer.build;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.RefSpec;
import org.slf4j.event.Level;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.g2forge.alexandria.data.graph.HGraph;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.alexandria.wizard.CommandLineStringInput;
import com.g2forge.alexandria.wizard.UserStringInput;
import com.g2forge.bulldozer.build.model.BulldozerProject;
import com.g2forge.bulldozer.build.model.Context;
import com.g2forge.gearbox.git.HGit;

import lombok.Data;

@Data
public class CreatePRs {
	public static void main(String[] args) throws JsonParseException, JsonMappingException, IOException, GitAPIException {
		final String branch = new CommandLineStringInput(args, 1).fallback(new UserStringInput("Branch", true)).get();
		final CreatePRs createPRs = new CreatePRs(new Context<BulldozerProject>(BulldozerProject::new, Paths.get(args[0])), branch);
		createPRs.createPRs();
	}

	protected final Context<BulldozerProject> context;

	protected final String branch;

	public void createPRs() throws InvalidRemoteException, TransportException, GitAPIException {
		HLog.getLogControl().setLogLevel(Level.INFO);
		// Fail if any repositories are dirty
		getContext().failIfDirty();

		// Find the projects which have the relevant branch
		final List<String> projects = getContext().getProjects().values().stream().filter(p -> HGit.isBranch(p.getGit(), getBranch())).map(BulldozerProject::getName).collect(Collectors.toList());
		// Topologically sort the projects
		final List<String> order = HGraph.toposort(projects, p -> HCollection.intersection(getContext().getNameToProject().get(p).getDependencies().getTransitive().keySet(), projects), false);

		// In topological order...
		for (String name : order) {
			final BulldozerProject project = getContext().getProjects().get(name);

			// Push the branch
			final String remote = HGit.getMyRemote(project.getGit());
			project.getGit().push().setRemote(remote).setRefSpecs(new RefSpec(getBranch() + ":" + getBranch())).call();

			// Open the PR
			// Set the assignee, master label & milestone
		}
	}
}
