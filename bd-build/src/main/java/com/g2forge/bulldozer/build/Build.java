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
import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.slf4j.event.Level;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.g2forge.alexandria.data.graph.HGraph;
import com.g2forge.alexandria.java.associative.cache.Cache;
import com.g2forge.alexandria.java.associative.cache.NeverCacheEvictionPolicy;
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
import com.g2forge.bulldozer.build.maven.Settings;
import com.g2forge.bulldozer.build.model.Project;
import com.g2forge.bulldozer.build.model.Projects;
import com.g2forge.gearbox.functional.proxy.Proxifier;
import com.g2forge.gearbox.functional.runner.ProcessBuilderRunner;
import com.g2forge.gearbox.git.HGit;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class Build {
	@Data
	@Builder
	@AllArgsConstructor
	protected static class ReleaseProperties {
		protected final String release;

		protected final String tag;

		protected final String development;
	}

	@Data
	protected class BuildProject implements ICloseable {
		protected final Project project;

		@Getter(lazy = true)
		private final String group = getMaven().evaluate(getDirectory(), "project.groupId");

		@Getter(lazy = true)
		private final Path directory = computeDirectory();

		@Getter(lazy = true)
		private final Git git = computeGit();

		@Getter(lazy = true, value = AccessLevel.PROTECTED)
		private final List<AutoCloseable> closeables = new ArrayList<>();

		@Getter(lazy = true)
		private final ReleaseProperties releaseProperties = computeReleaseProperties();

		protected ReleaseProperties computeReleaseProperties() {
			try (final InputStream stream = Files.newInputStream(getDirectory().resolve("release.properties"))) {
				final Properties properties = new Properties();
				properties.load(stream);
				final ReleasePropertiesBuilder retVal = ReleaseProperties.builder();
				retVal.tag(properties.getProperty("scm.tag"));
				final String suffix = getGroup() + "\\:" + getName();
				retVal.development(properties.getProperty("project.dev." + suffix));
				retVal.release(properties.getProperty("project.rel." + suffix));
				return retVal.build();
			} catch (IOException exception) {
				throw new RuntimeIOException(String.format("Failed to load release properties for %1$s", getName()), exception);
			}
		}

		@Override
		public void close() {
			HIO.closeAll(getCloseables());
		}

		protected Path computeDirectory() {
			final String name = getName();
			final int index = name.indexOf('/');
			return getRoot().resolve(index > 0 ? name.substring(0, index) : name);
		}

		protected Git computeGit() {
			final Git retVal = HGit.createGit(getDirectory(), false);
			getCloseables().add(retVal);
			return retVal;
		}

		public String getName() {
			return getProject().getName();
		}
	}

	protected static final List<String> updateProfiles = Stream.of(Project.Protection.values()).filter(p -> !Project.Protection.Public.equals(p)).map(p -> p.name().toLowerCase()).collect(Collectors.toList());

	protected static final XmlMapper mapper = new XmlMapper();

	protected static final String POMXML = "pom.xml";

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
				final List<BuildProject> dirty = getDirty(projects);
				if (!dirty.isEmpty()) throw new IllegalStateException(String.format("One or more projects were dirty (%1$s), please commit changes and try again!", dirty.stream().map(BuildProject::getName).collect(Collectors.joining(", "))));
			}

			// Compute the order in which to build the public projects
			log.info("Planning builder order");
			final List<String> order = computeBuildOrder(publicProjects);
			log.info("Build order: {}", order);

			// Prepare all the releases
			for (String name : order) {
				log.info("Preparing {}", name);
				final BuildProject project = projects.get(name);
				final Git git = project.getGit();

				// Create and switch to the release branch if needed
				final String current = git.getRepository().getBranch();
				if (!current.equals(getBranch())) git.checkout().setCreateBranch(true).setName(getBranch()).call();

				// Commit any changes from a past prepare
				commitUpstreamReversion(git);
				// TODO: Test this and everything later

				// Prepare the project (stream stdio to the console)
				getMaven().releasePrepare(project.getDirectory());

				// Check out the recent tag using jgit
				git.checkout().setStartPoint(project.getReleaseProperties().getTag()).call();

				// Maven install (stream stdio to the console) the newly created release version
				getMaven().install(project.getDirectory());

				// Update everyone who consumes this project (including the private consumers!) to the new version (and commit)
				log.info("Updating downstream {}", name);
				getMaven().updateVersions(getRoot(), false, updateProfiles, HCollection.asList(project.getGroup() + ":*"));
			}

			// Perform the releases
			for (String project : order) {
				log.info("Releasing {}", project);
				// getMaven().releasePerform(getRoot().resolve(project));
			}

			final Path repository;
			{ // Find the local maven repository
				final Settings settings = mapper.readValue(Paths.get(System.getProperty("user.home")).resolve(".m2/settings.xml").toFile(), Settings.class);
				repository = settings.getLocalRepository() == null ? Paths.get(System.getProperty("user.home")).resolve(".m2/repository") : Paths.get(settings.getLocalRepository());
			}

			// Cleanup
			for (String name : order) {
				log.info("Restarting development of {}", name);
				final BuildProject project = projects.get(name);

				// Remove the maven temporary install of the new release version
				Path current = repository;
				for (String component : project.getGroup().split("\\.")) {
					current = current.resolve(component);
				}
				final POM pom = mapper.readValue(project.getDirectory().resolve(POMXML).toFile(), POM.class);
				for (String artifact : HCollection.concatenate(HCollection.asList(name), pom.getModules())) {
					// TODO: Test this carefully
					HFile.delete(current.resolve(artifact).resolve(project.getReleaseProperties().getRelease()));
				}

				// Check out the branch head
				project.getGit().checkout().setCreateBranch(true).setName(getBranch()).call();

				// Maven install (stream stdio to the console) the new development versions
				getMaven().install(project.getDirectory());
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

	protected void commitUpstreamReversion(final Git git) throws GitAPIException, NoFilepatternException, NoHeadException, NoMessageException, UnmergedPathsException, ConcurrentRefUpdateException, WrongRepositoryStateException, AbortedByHookException {
		final Status status = git.status().call();
		if (!status.isClean() && !status.getUncommittedChanges().isEmpty()) {
			final AddCommand add = git.add();
			status.getUncommittedChanges().forEach(add::addFilepattern);
			add.call();
			git.commit().setMessage(getIssue() + " Updated upstream project versions").call();
		}
	}

	protected List<String> computeBuildOrder(final List<BuildProject> projects) {
		final Map<String, BuildProject> nameToProject = projects.stream().collect(Collectors.toMap(BuildProject::getName, IFunction1.identity()));
		final Map<String, BuildProject> groupToProject = projects.stream().collect(Collectors.toMap(BuildProject::getGroup, IFunction1.identity()));

		// Cache the project dependencies, where the dependencies are a map from the project to the version depended on
		final Cache<String, Map<String, String>> projectToDependencies = new Cache<String, Map<String, String>>(project -> {
			log.info("Loading dependencies for {}", project);
			// Run maven dependencies and filter the output down to usable information
			final Map<String, List<Descriptor>> grouped = getMaven().dependencyTree(getRoot().resolve(project), true, projects.stream().map(BuildProject::getGroup).map(g -> g + ":*").collect(Collectors.toList()))/*.map(new TapFunction<>(System.out::println))*/.filter(line -> {
				if (!line.startsWith("[INFO]")) return false;
				for (BuildProject publicProject : projects) {
					if (line.contains("- " + publicProject.getGroup())) return true;
				}
				return false;
			}).map(line -> Descriptor.fromString(line.substring(line.indexOf("- ") + 2))).filter(descriptor -> !descriptor.getGroup().equals(nameToProject.get(project).getGroup())).collect(Collectors.groupingBy(Descriptor::getGroup));
			// Extract the per-project version and make sure we only ever depend on one version
			final Map<String, String> versions = new LinkedHashMap<>();
			for (List<Descriptor> descriptors : grouped.values()) {
				final Set<String> groupVersions = descriptors.stream().map(Descriptor::getVersion).collect(Collectors.toSet());
				if (groupVersions.size() > 1) throw new IllegalArgumentException(String.format("Depended on multiple versions of the project \"%1$s\": %2$s", project, groupVersions));
				final String group = descriptors.get(0).getGroup();
				versions.put(groupToProject.get(group).getName(), HCollection.getOne(groupVersions));
			}
			log.info("Found dependencies for {}: {}", project, versions);
			return versions;
		}, NeverCacheEvictionPolicy.create());

		return HGraph.toposort(targets, p -> projectToDependencies.apply(p).keySet(), false);
	}

	protected String getBranch() {
		return issue + "-Temp" /* TODO */;
	}

	protected List<BuildProject> getDirty(final Map<String, BuildProject> projects) {
		return projects.values().stream().filter(project -> {
			try {
				return !project.getGit().status().call().isClean();
			} catch (NoWorkTreeException | GitAPIException e) {
				throw new RuntimeException(String.format("Failure while attempting to check whether %1$s is dirty!", project.getName()), e);
			}
		}).collect(Collectors.toList());
	}
}
