package com.g2forge.bulldozer.build;

import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.transport.FetchResult;
import org.slf4j.event.Level;

import com.g2forge.alexandria.adt.graph.v1.HGraph;
import com.g2forge.alexandria.command.command.IConstructorCommand;
import com.g2forge.alexandria.command.command.IStandardCommand;
import com.g2forge.alexandria.command.exit.IExit;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.fluent.optional.NullableOptional;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.alexandria.wizard.CommandLineStringInput;
import com.g2forge.alexandria.wizard.PropertyStringInput;
import com.g2forge.alexandria.wizard.UserStringInput;
import com.g2forge.bulldozer.build.model.BulldozerProject;
import com.g2forge.bulldozer.build.model.Context;
import com.g2forge.gearbox.git.GitConfig;
import com.g2forge.gearbox.git.HGit;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class CleanupPRs implements IConstructorCommand {
	public static final IStandardCommand COMMAND_FACTORY = IStandardCommand.of(invocation -> {
		final String branch = new CommandLineStringInput(invocation, 1).fallback(new UserStringInput("Branch", true)).get();
		return new CleanupPRs(new Context<BulldozerProject>(BulldozerProject::new, Paths.get(invocation.getArguments().get(0))), branch);
	});

	public static void main(String[] args) throws Throwable {
		IStandardCommand.main(args, COMMAND_FACTORY);
	}

	protected final Context<BulldozerProject> context;

	protected final String branch;
	
	protected final boolean allowDirty = new PropertyStringInput("bulldozer.allowdirty").map(Boolean::valueOf).fallback(NullableOptional.of(false)).get();

	@Override
	public IExit invoke() throws Throwable {
		HLog.getLogControl().setLogLevel(Level.INFO);
		// Fail if any repositories are dirty
		if (!allowDirty) getContext().failIfDirty();

		// Find the projects which have the relevant branch
		final List<String> projects = getContext().getProjects().values().stream().filter(p -> HGit.isBranch(p.getGit(), getBranch())).map(BulldozerProject::getName).collect(Collectors.toList());
		// Topologically sort the projects
		final List<String> order = HGraph.toposort(projects, p -> HCollection.intersection(getContext().getNameToProject().get(p).getDependencies().getTransitive().keySet(), projects), false);

		// In topological order...
		for (String name : order) {
			log.info("Cleaning up {}", name);

			final BulldozerProject project = getContext().getProjects().get(name);
			final Git git = project.getGit();
			final GitConfig config = new GitConfig(git);

			final FetchResult originFetch;
			{ // Fetch from the relevant remotes
				final String origin = config.getOrigin().getName();
				final String fork = config.getBranch(getBranch()).getRemote();
				log.info("Fetching from remotes {} and {}", origin, fork);
				originFetch = git.fetch().setRemote(origin).setTransportConfigCallback(getContext().getTransportConfig()).call();
				git.fetch().setRemote(fork).setRemoveDeletedRefs(true).setTransportConfigCallback(getContext().getTransportConfig()).call();
			}

			final ObjectId masterRemoteId;
			{ // Fast-forward the master branch
				final Ref masterLocalRef = git.getRepository().findRef(Constants.MASTER);
				final ObjectId masterLocalId = masterLocalRef.getObjectId();
				masterRemoteId = originFetch.getAdvertisedRef(config.getBranch(Constants.MASTER).getTracking()).getObjectId();

				final RefUpdate update = git.getRepository().updateRef(masterLocalRef.getTarget().getName());
				update.setNewObjectId(masterRemoteId);
				update.setExpectedOldObjectId(masterLocalId);
				update.setRefLogMessage("fast forward merge on cleanup", false);
				final Result result = update.update();
				if ((result != Result.FAST_FORWARD) && (result != Result.NO_CHANGE)) throw new RuntimeException("Failed to fast forward the local master branch!");
			}

			{ // Check that the PR has been merged
				log.info("Checking that the PR was merged into the master branch");
				final ObjectId branchId = git.getRepository().resolve(getBranch());
				if (!HGit.isMerged(git.getRepository(), masterRemoteId, branchId)) throw new RuntimeException("Pull request was not merged into master!");
			}

			// If we're on the branch we're cleaning up, then switch to origin
			if (git.getRepository().getBranch().equals(getBranch())) {
				log.info("Checking out master");
				git.checkout().setName(Constants.MASTER).call();
			}

			// Delete the PR branch
			log.info("Deleting PR branch");
			git.branchDelete().setBranchNames(getBranch()).call();

			log.info("{} is complete", name);
		}
		return IStandardCommand.SUCCESS;
	}
}
