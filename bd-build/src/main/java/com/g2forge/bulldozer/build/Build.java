package com.g2forge.bulldozer.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Constants;
import org.semver.Version;
import org.semver.Version.Element;
import org.slf4j.event.Level;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.g2forge.alexandria.data.graph.HGraph;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.alexandria.java.io.HFile;
import com.g2forge.alexandria.java.io.HIO;
import com.g2forge.alexandria.java.io.RuntimeIOException;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.bulldozer.build.maven.IMaven;
import com.g2forge.bulldozer.build.model.BulldozerProject;
import com.g2forge.bulldozer.build.model.Context;
import com.g2forge.bulldozer.build.model.maven.MavenProject;
import com.g2forge.bulldozer.build.model.maven.MavenProjects;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class Build {
	public static class BuildProject extends BulldozerProject {
		@Getter(lazy = true)
		private final ReleaseProperties releaseProperties = computeReleaseProperties();

		public BuildProject(Context context, MavenProject project) {
			super(context, project);
		}

		public void checkoutTag(String tag) throws RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException, CheckoutConflictException, GitAPIException {
			getGit().checkout().setCreateBranch(true).setName(BRANCH_DUMMY).setStartPoint(Constants.R_TAGS + tag).call();
			this.getCloseables().add(() -> getGit().branchDelete().setBranchNames(BRANCH_DUMMY).call());
		}

		protected ReleaseProperties computeReleaseProperties() {
			final Path file = getDirectory().resolve(IMaven.RELEASE_PROPERTIES);
			try (final InputStream stream = Files.newInputStream(file)) {
				final Properties properties = new Properties();
				properties.load(stream);

				final ReleaseProperties.ReleasePropertiesBuilder retVal = ReleaseProperties.builder();
				retVal.tag(properties.getProperty("scm.tag"));
				final String suffix = getGroup() + ":" + getName();
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
			final Version prior = Version.parse(getVersion());
			final Version release = prior.toReleaseVersion();
			final String releaseVersion = release.toString();
			retVal.release(releaseVersion);
			retVal.tag(releaseVersion);
			retVal.development(release.next(Element.PATCH).toString() + "-SNAPSHOT");
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

	public enum Phase {
		Initial,
		Prepared,
		InstalledRelease,
		Released,
		DeletedRelease,
		InstalledDevelopment,
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

	public static void main(String[] args) throws JsonParseException, JsonMappingException, IOException, GitAPIException {
		final Build build = new Build(new Context(Paths.get(args[0])), args[1], HCollection.asList(Arrays.copyOfRange(args, 2, args.length)));
		build.build();
	}

	protected final Context context;

	protected final String issue;

	protected final List<String> targets;

	public void build() throws IOException, GitAPIException {
		HLog.getLogControl().setLogLevel(Level.INFO);
		log.info("Building: {}", getTargets());

		// Load information about all the projects
		log.info("Loading project information");
		final Map<String, BuildProject> projects = new MavenProjects(getContext().getRoot().resolve(IMaven.POM_XML)).getProjects().stream().map(project -> new BuildProject(getContext(), project)).collect(Collectors.toMap(BulldozerProject::getName, IFunction1.identity()));
		try {
			// Print a list of the public projects
			final List<BuildProject> publicProjects = projects.values().stream().filter(project -> MavenProject.Protection.Public.equals(project.getProject().getProtection())).collect(Collectors.toList());
			log.info("Public projects: {}", publicProjects.stream().map(BulldozerProject::getName).collect(Collectors.joining(", ")));

			{// Check for uncommitted changes in any project, and fail
				final List<BulldozerProject> dirty = projects.values().stream().filter(project -> {
					try {
						final Status status = project.getGit().status().call();
						return !status.isClean() && !status.getUncommittedChanges().isEmpty();
					} catch (NoWorkTreeException | GitAPIException e) {
						throw new RuntimeException(String.format("Failure while attempting to check whether %1$s is dirty!", project.getName()), e);
					}
				}).collect(Collectors.toList());
				if (!dirty.isEmpty()) throw new IllegalStateException(String.format("One or more projects were dirty (%1$s), please commit changes and try again!", dirty.stream().map(BulldozerProject::getName).collect(Collectors.joining(", "))));
			}

			// Compute the order in which to build the public projects
			log.info("Planning builder order");
			final List<String> order = computeBuildOrder(publicProjects);
			log.info("Build order: {}", order);

			{ // Make sure none of the tags already exist
				final List<BuildProject> tagged = order.stream().map(projects::get).filter(project -> {
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
				log.info("Preparing {}", name);
				final BuildProject project = projects.get(name);
				final Git git = project.getGit();

				Phase phase = project.getPhase();
				if (Phase.Prepared.compareTo(phase) > 0) {
					// Create and switch to the release branch if needed
					switchToBranch(git);
					// Commit any changes from a past prepare
					commitUpstreamReversion(git);

					{ // Prepare the project (stream stdio to the console)
						final ReleaseProperties releaseProperties = project.predictReleaseProperties();
						getContext().getMaven().releasePrepare(project.getDirectory(), releaseProperties.getTag(), releaseProperties.getRelease(), releaseProperties.getDevelopment());
					}
					phase = project.updatePhase(Phase.Prepared);
				}

				if (Phase.InstalledRelease.compareTo(phase) > 0) {
					// Check out the recent tag using jgit
					project.checkoutTag(project.getReleaseProperties().getTag());
					// Maven install (stream stdio to the console) the newly created release version
					getContext().getMaven().install(project.getDirectory());

					phase = project.updatePhase(Phase.InstalledRelease);
				}

				// Update everyone who consumes this project (including the private consumers!) to the new version (and commit)
				log.info("Updating downstream {}", name);
				for (BulldozerProject downstream : projects.values()) {
					// Skip ourselves
					if (downstream == project) continue;
					getContext().getMaven().updateVersions(downstream.getDirectory(), false, PROFILES_TO_UPDATE, HCollection.asList(project.getGroup() + ":*"));
				}
			}

			// Perform the releases
			for (String name : order) {
				log.info("Releasing {}", name);
				final BuildProject project = projects.get(name);

				if (Phase.Released.compareTo(project.getPhase()) > 0) {
					final Git git = project.getGit();

					// Check out the branch head
					git.checkout().setCreateBranch(false).setName(getBranch()).call();

					// Perform the release
					getContext().getMaven().releasePerform(project.getDirectory());

					project.updatePhase(Phase.Released);
				}
			}

			// Find the local maven repository
			final Path repository = Paths.get(getContext().getMaven().evaluate(getContext().getRoot(), "settings.localRepository"));
			// Cleanup
			for (String name : order) {
				log.info("Restarting development of {}", name);
				final BuildProject project = projects.get(name);
				Phase phase = project.getPhase();

				if (Phase.DeletedRelease.compareTo(phase) > 0) {
					// Remove the maven temporary install of the new release version
					Path current = repository;
					for (String component : project.getGroup().split("\\.")) {
						current = current.resolve(component);
					}
					for (String artifact : HCollection.concatenate(HCollection.asList(name), project.getPom().getModules())) {
						HFile.delete(current.resolve(artifact).resolve(project.getReleaseProperties().getRelease()));
					}
					phase = project.updatePhase(Phase.DeletedRelease);
				}

				if (Phase.InstalledDevelopment.compareTo(phase) > 0) {
					// Check out the branch head
					project.getGit().checkout().setCreateBranch(false).setName(getBranch()).call();

					// Maven install (stream stdio to the console) the new development versions
					getContext().getMaven().install(project.getDirectory());
					phase = project.updatePhase(Phase.InstalledDevelopment);
				}
			}

			log.info("Updating downstream projects");
			for (BulldozerProject project : projects.values()) {
				// Update all the downstreams to new snapshot versions
				getContext().getMaven().updateVersions(project.getDirectory(), true, PROFILES_TO_UPDATE, order.stream().map(projects::get).map(BulldozerProject::getGroup).map(g -> g + ":*").collect(Collectors.toList()));
				// Commit anything dirty, since those are the things with version updates
				commitUpstreamReversion(project.getGit());
			}
		} finally {
			HIO.closeAll(projects.values());
		}
	}

	protected void commitUpstreamReversion(final Git git) throws IOException, GitAPIException {
		final Status status = git.status().call();
		if (!status.isClean() && !status.getUncommittedChanges().isEmpty()) {
			switchToBranch(git);

			final AddCommand add = git.add();
			status.getUncommittedChanges().forEach(add::addFilepattern);
			add.call();
			git.commit().setMessage(getIssue() + " Updated upstream project versions").call();
		}
	}

	protected List<String> computeBuildOrder(final List<? extends BulldozerProject> projects) {
		final Map<String, ? extends BulldozerProject> nameToProject = projects.stream().collect(Collectors.toMap(BulldozerProject::getName, IFunction1.identity()));
		final Map<String, ? extends BulldozerProject> groupToProject = projects.stream().collect(Collectors.toMap(BulldozerProject::getGroup, IFunction1.identity()));
		return HGraph.toposort(targets, p -> nameToProject.get(p).loadDependencies(nameToProject, groupToProject).keySet(), false);
	}

	protected String getBranch() {
		return getIssue() + "-Release";
	}

	protected void switchToBranch(final Git git) throws IOException, GitAPIException {
		final String current = git.getRepository().getBranch();
		if (!current.equals(getBranch())) {
			final boolean exists = git.getRepository().findRef(Constants.R_HEADS + getBranch()) != null;
			git.checkout().setCreateBranch(!exists).setName(getBranch()).call();
		}
	}
}
