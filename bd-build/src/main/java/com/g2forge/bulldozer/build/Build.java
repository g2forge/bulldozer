package com.g2forge.bulldozer.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.g2forge.alexandria.data.graph.HGraph;
import com.g2forge.alexandria.java.close.ICloseable;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.alexandria.java.io.HFile;
import com.g2forge.alexandria.java.io.HIO;
import com.g2forge.alexandria.java.io.RuntimeIOException;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.bulldozer.build.Build.ReleaseProperties.ReleasePropertiesBuilder;
import com.g2forge.bulldozer.build.maven.Descriptor;
import com.g2forge.bulldozer.build.maven.IMaven;
import com.g2forge.bulldozer.build.maven.POM;
import com.g2forge.bulldozer.build.model.Project;
import com.g2forge.bulldozer.build.model.Projects;
import com.g2forge.gearbox.functional.proxy.Proxifier;
import com.g2forge.gearbox.functional.runner.ProcessBuilderRunner;
import com.g2forge.gearbox.git.HGit;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class Build {
	@Data
	@EqualsAndHashCode(exclude = "dependencies")
	protected class BuildProject implements ICloseable {
		protected final Project project;

		@Getter(lazy = true)
		private final String group = getMaven().evaluate(getDirectory(), "project.groupId");

		@Getter(lazy = true)
		private final String version = getMaven().evaluate(getDirectory(), "project.version");

		@Getter(lazy = true)
		private final Path directory = computeDirectory();

		@Getter(lazy = true)
		private final Git git = computeGit();

		@Getter(lazy = true)
		private final POM pom = computePOM();

		protected Map<String, String> dependencies;

		@Getter(lazy = true, value = AccessLevel.PROTECTED)
		private final List<AutoCloseable> closeables = new ArrayList<>();

		@Getter(lazy = true)
		private final ReleaseProperties releaseProperties = computeReleaseProperties();

		public void checkoutTag(String tag) throws RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException, CheckoutConflictException, GitAPIException {
			getGit().checkout().setCreateBranch(true).setName(BRANCH_DUMMYINSTALL).setStartPoint(Constants.R_TAGS + tag).call();
			this.getCloseables().add(() -> getGit().branchDelete().setBranchNames(BRANCH_DUMMYINSTALL).call());
		}

		@Override
		public void close() {
			HIO.closeAll(getCloseables());
		}

		protected Path computeDirectory() {
			return getRoot().resolve(getName());
		}

		protected Git computeGit() {
			final String name = getName();
			final int index = name.indexOf('/');
			final Git retVal = HGit.createGit(getRoot().resolve(index > 0 ? name.substring(0, index) : name), false);
			getCloseables().add(retVal);
			return retVal;
		}

		protected POM computePOM() {
			try {
				return xmlMapper.readValue(getDirectory().resolve(POMXML).toFile(), POM.class);
			} catch (IOException e) {
				throw new RuntimeIOException(String.format("Failed to read %1$s for %2$s!", POMXML, getName()), e);
			}
		}

		protected ReleaseProperties computeReleaseProperties() {
			final Path file = getDirectory().resolve(RELEASE_PROPERTIES);
			try (final InputStream stream = Files.newInputStream(file)) {
				final Properties properties = new Properties();
				properties.load(stream);

				final ReleasePropertiesBuilder retVal = ReleaseProperties.builder();
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

		public String getName() {
			return getProject().getName();
		}

		public Phase getPhase() {
			final Path path = getDirectory().resolve(BULLDOZER_STATE);
			if (Files.exists(path)) {
				try {
					return objectMapper.readValue(path.toFile(), State.class).getPhase();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			} else return Phase.Initial;
		}

		public Map<String, String> loadDependencies(final Map<String, BuildProject> nameToProject, final Map<String, BuildProject> groupToProject) {
			if (dependencies == null) {
				final String commit;
				try {
					commit = getGit().getRepository().findRef(Constants.HEAD).getObjectId().getName();
				} catch (IOException e) {
					throw new RuntimeIOException(e);
				}

				final Path path = getDirectory().resolve(BULLDOZER_TEMP);
				if (Files.exists(path)) {
					try {
						final Temp temp = objectMapper.readValue(path.toFile(), Temp.class);
						if (temp.getHash().equals(commit)) dependencies = temp.getDependencies();
						else Files.delete(path);
					} catch (IOException e) {
						throw new RuntimeIOException(e);
					}
				}

				if (dependencies == null) {
					final String name = getName();
					log.info("Loading dependencies for {}", name);
					// Run maven dependencies and filter the output down to usable information
					final Map<String, List<Descriptor>> grouped = getMaven().dependencyTree(getRoot().resolve(name), true, groupToProject.keySet().stream().map(g -> g + ":*").collect(Collectors.toList()))/*.map(new TapFunction<>(System.out::println))*/.filter(line -> {
						if (!line.startsWith("[INFO]")) return false;
						for (BuildProject publicProject : nameToProject.values()) {
							if (line.contains("- " + publicProject.getGroup())) return true;
						}
						return false;
					}).map(line -> Descriptor.fromString(line.substring(line.indexOf("- ") + 2))).filter(descriptor -> !descriptor.getGroup().equals(nameToProject.get(name).getGroup())).collect(Collectors.groupingBy(Descriptor::getGroup));
					// Extract the per-project version and make sure we only ever depend on one version
					dependencies = new LinkedHashMap<>();
					for (List<Descriptor> descriptors : grouped.values()) {
						final Set<String> groupVersions = descriptors.stream().map(Descriptor::getVersion).collect(Collectors.toSet());
						if (groupVersions.size() > 1) throw new IllegalArgumentException(String.format("%3$s depends on multiple versions of the project \"%1$s\": %2$s", descriptors.get(0).getGroup(), groupVersions, name));
						final String group = descriptors.get(0).getGroup();
						dependencies.put(groupToProject.get(group).getName(), HCollection.getOne(groupVersions));
					}
					log.info("Found dependencies for {}: {}", name, dependencies);
					try {
						objectMapper.writeValue(path.toFile(), new Temp(commit, dependencies));
					} catch (IOException e) {
						throw new RuntimeIOException(e);
					}
				}
			}
			return dependencies;
		}

		public ReleaseProperties predictReleaseProperties() {
			final ReleasePropertiesBuilder retVal = ReleaseProperties.builder();
			final Version prior = Version.parse(getVersion());
			final Version release = prior.toReleaseVersion();
			final String releaseVersion = release.toString();
			retVal.release(releaseVersion);
			retVal.tag(releaseVersion);
			retVal.development(release.next(Element.PATCH).toString() + "-SNAPSHOT");
			return retVal.build();
		}

		public Phase updatePhase(Phase phase) {
			final Path path = getDirectory().resolve(BULLDOZER_STATE);
			try {
				objectMapper.writeValue(path.toFile(), new State(phase));
			} catch (IOException exception) {
				throw new RuntimeIOException(String.format("Failed to update the phase %1$s", getName()), exception);
			}
			return phase;
		}
	}

	protected static enum Phase {
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
	protected static class ReleaseProperties {
		protected final String release;

		protected final String tag;

		protected final String development;

		protected final String completed;
	}

	@Data
	@Builder
	@AllArgsConstructor
	public static class State {
		protected final Phase phase;
	}

	@Data
	@Builder
	@AllArgsConstructor
	public static class Temp {
		protected final String hash;

		@Singular
		protected final Map<String, String> dependencies;
	}

	protected static final String BULLDOZER_STATE = "bulldozer-state.json";

	protected static final String BULLDOZER_TEMP = "bulldozer-temp.json";

	protected static final List<String> updateProfiles = Stream.of(Project.Protection.values()).filter(p -> !Project.Protection.Public.equals(p)).map(p -> p.name().toLowerCase()).collect(Collectors.toList());

	protected static final XmlMapper xmlMapper = new XmlMapper();

	protected static final ObjectMapper objectMapper = new ObjectMapper();

	protected static final String RELEASE_PROPERTIES = "release.properties";

	protected static final String POMXML = "pom.xml";

	protected static final String BRANCH_DUMMYINSTALL = "DummyInstall";

	public static void main(String[] args) throws JsonParseException, JsonMappingException, IOException, GitAPIException {
		final Build build = new Build(Paths.get(args[0]), args[1], HCollection.asList(Arrays.copyOfRange(args, 2, args.length)));
		build.build();
	}

	@Getter(AccessLevel.PROTECTED)
	protected final IMaven maven = new Proxifier().generate(new ProcessBuilderRunner(), IMaven.class);

	protected final Path root;

	protected final String issue;

	protected final List<String> targets;

	public void build() throws IOException, GitAPIException {
		HLog.getLogControl().setLogLevel(Level.INFO);
		log.info("Building: {}", getTargets());

		// Load information about all the projects
		log.info("Loading project information");
		final Map<String, BuildProject> projects = new Projects(getRoot().resolve(POMXML)).getProjects().stream().map(BuildProject::new).collect(Collectors.toMap(BuildProject::getName, IFunction1.identity()));
		try {
			// Print a list of the public projects
			final List<BuildProject> publicProjects = projects.values().stream().filter(project -> Project.Protection.Public.equals(project.getProject().getProtection())).collect(Collectors.toList());
			log.info("Public projects: {}", publicProjects.stream().map(BuildProject::getName).collect(Collectors.joining(", ")));

			{// Check for uncommitted changes in any project, and fail
				final List<BuildProject> dirty = projects.values().stream().filter(project -> {
					try {
						final Status status = project.getGit().status().call();
						return !status.isClean() && !status.getUncommittedChanges().isEmpty();
					} catch (NoWorkTreeException | GitAPIException e) {
						throw new RuntimeException(String.format("Failure while attempting to check whether %1$s is dirty!", project.getName()), e);
					}
				}).collect(Collectors.toList());
				if (!dirty.isEmpty()) throw new IllegalStateException(String.format("One or more projects were dirty (%1$s), please commit changes and try again!", dirty.stream().map(BuildProject::getName).collect(Collectors.joining(", "))));
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
				if (!tagged.isEmpty()) throw new IllegalStateException(String.format("One or more projects were already tagged (%1$s), please remove those tags and try again!", tagged.stream().map(BuildProject::getName).collect(Collectors.joining(", "))));
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
						getMaven().releasePrepare(project.getDirectory(), releaseProperties.getTag(), releaseProperties.getRelease(), releaseProperties.getDevelopment());
					}
					phase = project.updatePhase(Phase.Prepared);
				}

				if (Phase.InstalledRelease.compareTo(phase) > 0) {
					// Check out the recent tag using jgit
					project.checkoutTag(project.getReleaseProperties().getTag());
					// Maven install (stream stdio to the console) the newly created release version
					getMaven().install(project.getDirectory());

					phase = project.updatePhase(Phase.InstalledRelease);
				}

				// Update everyone who consumes this project (including the private consumers!) to the new version (and commit)
				log.info("Updating downstream {}", name);
				for (BuildProject downstream : projects.values()) {
					// Skip ourselves
					if (downstream == project) continue;
					getMaven().updateVersions(downstream.getDirectory(), false, updateProfiles, HCollection.asList(project.getGroup() + ":*"));
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
					// getMaven().releasePerform(project.getDirectory());

					project.updatePhase(Phase.Released);
				}
			}

			// Find the local maven repository
			final Path repository = Paths.get(getMaven().evaluate(getRoot(), "settings.localRepository"));
			// Cleanup
			for (String name : order) {
				log.info("Restarting development of {}", name);
				final BuildProject project = projects.get(name);
				Phase phase = project.getPhase();

				if (Phase.Released.compareTo(phase) > 0) {
					// Remove the maven temporary install of the new release version
					Path current = repository;
					for (String component : project.getGroup().split("\\.")) {
						current = current.resolve(component);
					}
					for (String artifact : HCollection.concatenate(HCollection.asList(name), project.getPom().getModules())) {
						HFile.delete(current.resolve(artifact).resolve(project.getReleaseProperties().getRelease()));
					}
					project.updatePhase(Phase.DeletedRelease);
				}

				if (Phase.InstalledDevelopment.compareTo(phase) > 0) {
					// Check out the branch head
					project.getGit().checkout().setCreateBranch(false).setName(getBranch()).call();

					// Maven install (stream stdio to the console) the new development versions
					getMaven().install(project.getDirectory());
					project.updatePhase(Phase.InstalledDevelopment);
				}
			}

			{
				log.info("Updating downstream projects");
				// Update all the downstreams to new snapshot versions
				getMaven().updateVersions(getRoot(), true, updateProfiles, order.stream().map(projects::get).map(BuildProject::getGroup).map(g -> g + ":*").collect(Collectors.toList()));

				// Commit anything dirty, since those are the things with version updates
				for (BuildProject project : projects.values()) {
					commitUpstreamReversion(project.getGit());
				}
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

	protected List<String> computeBuildOrder(final List<BuildProject> projects) {
		final Map<String, BuildProject> nameToProject = projects.stream().collect(Collectors.toMap(BuildProject::getName, IFunction1.identity()));
		final Map<String, BuildProject> groupToProject = projects.stream().collect(Collectors.toMap(BuildProject::getGroup, IFunction1.identity()));
		return HGraph.toposort(targets, p -> nameToProject.get(p).loadDependencies(nameToProject, groupToProject).keySet(), false);
	}

	protected String getBranch() {
		return getIssue() + "-Release";
	}

	protected void switchToBranch(final Git git) throws IOException, GitAPIException {
		final String current = git.getRepository().getBranch();
		if (!current.equals(getBranch())) {
			final boolean exists = git.getRepository().findRef(Constants.R_HEADS + getBranch()) != null;
			git.checkout().setCreateBranch(exists).setName(getBranch()).call();
		}
	}
}
