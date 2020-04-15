package com.g2forge.bulldozer.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.semver.Version;
import org.semver.Version.Element;
import org.slf4j.event.Level;

import com.g2forge.alexandria.adt.graph.HGraph;
import com.g2forge.alexandria.command.command.IConstructorCommand;
import com.g2forge.alexandria.command.command.IStandardCommand;
import com.g2forge.alexandria.command.exit.IExit;
import com.g2forge.alexandria.java.close.ICloseable;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.fluent.optional.NullableOptional;
import com.g2forge.alexandria.java.io.HIO;
import com.g2forge.alexandria.java.io.RuntimeIOException;
import com.g2forge.alexandria.java.io.file.HFile;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.alexandria.wizard.PropertyStringInput;
import com.g2forge.bulldozer.build.maven.IMaven;
import com.g2forge.bulldozer.build.model.BulldozerProject;
import com.g2forge.bulldozer.build.model.Context;
import com.g2forge.bulldozer.build.model.maven.MavenProject;
import com.g2forge.gearbox.git.HGit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class Release implements IConstructorCommand {
	public enum Phase {
		Initial,
		Prepared,
		InstalledRelease,
		Released,
		InstalledDevelopment,
		DeletedRelease,
	}

	public static class ReleaseProject extends BulldozerProject {
		@Getter(lazy = true)
		private final ReleaseProperties releaseProperties = computeReleaseProperties();

		public ReleaseProject(Context<ReleaseProject> context, MavenProject project) {
			super(context, project);
		}

		public void checkoutTag(String tag) throws RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException, CheckoutConflictException, GitAPIException, IOException {
			final String previous = getGit().getRepository().getBranch();
			getGit().checkout().setCreateBranch(true).setName(BRANCH_DUMMY).setStartPoint(Constants.R_TAGS + tag).call();
			this.getCloseables().add(() -> {
				getGit().checkout().setName(previous).call();
				getGit().branchDelete().setBranchNames(BRANCH_DUMMY).call();
			});
		}

		protected ReleaseProperties computeReleaseProperties() {
			final Path file = getDirectory().resolve(IMaven.RELEASE_PROPERTIES);
			try (final InputStream stream = Files.newInputStream(file)) {
				final Properties properties = new Properties();
				properties.load(stream);

				final ReleaseProperties.ReleasePropertiesBuilder retVal = ReleaseProperties.builder();
				retVal.tag(properties.getProperty("scm.tag"));
				final String suffix = getGroup() + ":" + getArtifactId();
				retVal.development(properties.getProperty("project.dev." + suffix));
				retVal.release(properties.getProperty("project.rel." + suffix));
				retVal.completed(properties.getProperty("completedPhase"));
				return retVal.build();
			} catch (IOException exception) {
				throw new RuntimeIOException(String.format("Failed to load release properties for %1$s", getName()), exception);
			}
		}

		public Phase getPhase() {
			final Path path = getDirectory().resolve(State.BULLDOZER_STATE);
			if (Files.exists(path)) {
				try {
					return getContext().getObjectMapper().readValue(path.toFile(), State.class).getPhase();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			} else return Phase.Initial;
		}

		public ReleaseProperties predictReleaseProperties() {
			final ReleaseProperties.ReleasePropertiesBuilder retVal = ReleaseProperties.builder();
			final Version release = Version.parse(getVersion()).toReleaseVersion();
			final String releaseVersion = release.toString();
			retVal.release(releaseVersion);
			retVal.tag(releaseVersion);
			retVal.development(release.next(Element.PATCH).toString() + IMaven.SNAPSHOT);
			return retVal.build();
		}

		public Phase updatePhase(Phase phase) {
			final Path path = getDirectory().resolve(State.BULLDOZER_STATE);
			try {
				getContext().getObjectMapper().writeValue(path.toFile(), new State(phase));
			} catch (IOException exception) {
				throw new RuntimeIOException(String.format("Failed to update the phase %1$s", getName()), exception);
			}
			return phase;
		}
	}

	@Data
	@Builder
	@AllArgsConstructor
	public static class ReleaseProperties {
		protected final String release;

		protected final String tag;

		protected final String development;

		protected final String completed;
	}

	@Data
	@Builder
	@AllArgsConstructor
	public static class State {
		public static final String BULLDOZER_STATE = "bulldozer-state.json";

		protected final Phase phase;
	}

	protected static final List<String> PROFILES_TO_UPDATE = Stream.of(MavenProject.Protection.values()).filter(p -> !MavenProject.Protection.Public.equals(p)).map(p -> p.name().toLowerCase()).collect(Collectors.toList());

	public static final IStandardCommand COMMAND_FACTORY = IStandardCommand.of(invocation -> {
		final List<String> arguments = invocation.getArguments();
		return new Release(new Context<ReleaseProject>(ReleaseProject::new, Paths.get(arguments.get(0))), arguments.get(1), arguments.subList(2, arguments.size()));
	});

	public static void main(String[] args) throws Throwable {
		IStandardCommand.main(args, COMMAND_FACTORY);
	}

	protected final Context<ReleaseProject> context;

	protected final String issue;

	protected final List<String> targets;

	protected final boolean allowDirty = new PropertyStringInput("bulldozer.allowdirty").map(Boolean::valueOf).fallback(NullableOptional.of(false)).get();

	protected void commitUpstreamReversion(final Git git) throws IOException, GitAPIException {
		final Status status = git.status().call();
		if (!status.isClean() && !status.getUncommittedChanges().isEmpty()) {
			final AddCommand add = git.add();
			status.getUncommittedChanges().forEach(add::addFilepattern);
			add.call();
			git.commit().setMessage(getIssue() + " Updated upstream project versions").call();
		}
	}

	protected String getBranch() {
		return getIssue() + "-Release";
	}

	@Override
	public IExit invoke() throws Throwable {
		HLog.getLogControl().setLogLevel(Level.INFO);
		log.info("Releasing: {}", getTargets());

		// Load information about all the projects
		log.info("Loading project information");
		try (ICloseable closeProjects = () -> HIO.closeAll(getContext().getProjects().values())) {
			// Print a list of the public projects
			final List<ReleaseProject> publicProjects = getContext().getProjects().values().stream().filter(project -> MavenProject.Protection.Public.equals(project.getProject().getProtection())).collect(Collectors.toList());
			log.info("Public projects: {}", publicProjects.stream().map(BulldozerProject::getName).collect(Collectors.joining(", ")));

			// Check for uncommitted changes in any project, and fail
			if (!allowDirty) getContext().failIfDirty();

			// Compute the order in which to release the public projects
			log.info("Planning release order");
			final List<String> order = HGraph.toposort(targets, p -> getContext().getNameToProject().get(p).getDependencies().getTransitive().keySet(), false);
			log.info("Release order: {}", order);

			if (!allowDirty) { // Make sure none of the tags already exist
				final List<ReleaseProject> tagged = order.stream().map(getContext().getProjects()::get).filter(project -> {
					final ReleaseProperties releaseProperties = project.predictReleaseProperties();
					try {
						return project.getGit().getRepository().findRef(Constants.R_TAGS + releaseProperties.getTag()) != null;
					} catch (IOException e) {
						throw new RuntimeException(String.format("Failure while attempting to check whether %1$s is already tagged!", project.getName()), e);
					}
				}).collect(Collectors.toList());
				if (!tagged.isEmpty()) throw new IllegalStateException(String.format("One or more projects were already tagged (%1$s), please remove those tags and try again!", tagged.stream().map(BulldozerProject::getName).collect(Collectors.joining(", "))));
			}

			// Prepare all the releases
			for (String name : order) {
				final ReleaseProject project = getContext().getProjects().get(name);
				final Git git = project.getGit();
				final ReleaseProperties releaseProperties = project.predictReleaseProperties();
				
				Phase phase = project.getPhase();
				if (Phase.Prepared.compareTo(phase) > 0) {
					log.info("Preparing {} {}", name, releaseProperties.getRelease());
					// Create and switch to the release branch if needed
					switchToBranch(git);
					// Commit any changes from a past prepare
					commitUpstreamReversion(git);

					// Prepare the project (stream stdio to the console)
					getContext().getMaven().releasePrepare(project.getDirectory(), releaseProperties.getTag(), releaseProperties.getRelease(), releaseProperties.getDevelopment());
					phase = project.updatePhase(Phase.Prepared);
				}
				log.info("Prepared {} {}", name, releaseProperties.getRelease());

				if (Phase.InstalledRelease.compareTo(phase) > 0) {
					log.info("Installing release {} {}", name, releaseProperties.getRelease());
					// Check out the recent tag using jgit
					project.checkoutTag(project.getReleaseProperties().getTag());
					// Maven install (stream stdio to the console) the newly created release version
					getContext().getMaven().install(project.getDirectory());

					// Update everyone who consumes this project (including the private consumers!) to the new version (and commit)
					log.info("Updating downstreams {} {}", name, releaseProperties.getRelease());
					for (BulldozerProject downstream : getContext().getProjects().values()) {
						// Skip ourselves & projects that don't depend on us
						if ((downstream == project) || !downstream.getDependencies().getTransitive().keySet().contains(name)) continue;
						// Update all the downstreams to new release versions
						switchToBranch(downstream.getGit());
						getContext().getMaven().updateVersions(downstream.getDirectory(), downstream.getParentGroup().equals(project.getGroup()), false, PROFILES_TO_UPDATE, HCollection.asList(project.getGroup() + ":*"));
					}

					phase = project.updatePhase(Phase.InstalledRelease);
				}
				log.info("Installed release {} {}", name, releaseProperties.getRelease());
			}

			// Perform the releases
			for (String name : order) {
				final ReleaseProject project = getContext().getProjects().get(name);

				if (Phase.Released.compareTo(project.getPhase()) > 0) {
					log.info("Releasing {}", name);
					final Git git = project.getGit();

					// Check out the branch head
					git.checkout().setCreateBranch(false).setName(getBranch()).call();
					// Perform the release
					getContext().getMaven().releasePerform(project.getDirectory());

					project.updatePhase(Phase.Released);
				}
				log.info("Released {}", name);
			}

			// Restarting development
			for (String name : order) {
				final ReleaseProject project = getContext().getProjects().get(name);
				Phase phase = project.getPhase();

				if (Phase.InstalledDevelopment.compareTo(phase) > 0) {
					log.info("Restarting development of {}", name);
					// Check out the branch head
					project.getGit().checkout().setCreateBranch(false).setName(getBranch()).call();
					// Maven install (stream stdio to the console) the new development versions
					getContext().getMaven().install(project.getDirectory());

					log.info("Updating downstream {}", name);
					for (BulldozerProject downstream : getContext().getProjects().values()) {
						// Skip ourselves & projects that don't depend on us
						if ((downstream == project) || !downstream.getDependencies().getTransitive().keySet().contains(name)) continue;
						// Update all the downstreams to new snapshot versions
						switchToBranch(downstream.getGit());
						getContext().getMaven().updateVersions(downstream.getDirectory(), downstream.getParentGroup().equals(project.getGroup()), true, PROFILES_TO_UPDATE, HCollection.asList(project.getGroup() + ":*"));
					}

					phase = project.updatePhase(Phase.InstalledDevelopment);
				}
				log.info("Restarted development of {}", name);
			}

			// Commit anything dirty, since those are the things with version updates
			log.info("Committing downstream projects");
			for (BulldozerProject project : getContext().getProjects().values()) {
				// Commit anything dirty, since those are the things with version updates
				switchToBranch(project.getGit());
				commitUpstreamReversion(project.getGit());
			}

			// Find the local maven repository
			final Path repository = Paths.get(getContext().getMaven().evaluate(getContext().getRoot(), "settings.localRepository"));
			// Cleanup
			for (String name : order) {
				final ReleaseProject project = getContext().getProjects().get(name);
				Phase phase = project.getPhase();

				if (Phase.DeletedRelease.compareTo(phase) > 0) {
					log.info("Cleaning up temporary release install of {}", name);
					final String releaseVersion = Version.parse(project.getVersion()).toReleaseVersion().toString();
					// Remove the maven temporary install of the new release version
					Path current = repository;
					for (String component : project.getGroup().split("\\.")) {
						current = current.resolve(component);
					}
					for (String artifact : HCollection.concatenate(HCollection.asList(name), project.getPom().getModules())) {
						HFile.delete(current.resolve(artifact).resolve(releaseVersion));
					}
					phase = project.updatePhase(Phase.DeletedRelease);
				}
				log.info("Cleaned up temporary release install of {}", name);
			}
		}
		return SUCCESS;
	}

	protected void switchToBranch(final Git git) throws IOException, GitAPIException {
		final String branch = getBranch();
		if (!branch.equals(git.getRepository().getBranch())) {
			final boolean exists = HGit.isBranch(git, branch);
			git.checkout().setCreateBranch(!exists).setName(branch).call();
		}
	}
}
