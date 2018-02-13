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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.slf4j.event.Level;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.g2forge.alexandria.data.graph.HGraph;
import com.g2forge.alexandria.java.associative.cache.Cache;
import com.g2forge.alexandria.java.associative.cache.NeverCacheEvictionPolicy;
import com.g2forge.alexandria.java.close.ICloseable;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.alexandria.java.io.HIO;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.bulldozer.build.maven.Descriptor;
import com.g2forge.bulldozer.build.maven.IMaven;
import com.g2forge.bulldozer.build.model.Project;
import com.g2forge.bulldozer.build.model.Projects;
import com.g2forge.gearbox.functional.proxy.Proxifier;
import com.g2forge.gearbox.functional.runner.ProcessBuilderRunner;
import com.g2forge.gearbox.git.HGit;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class Build {
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

	public static void main(String[] args) throws JsonParseException, JsonMappingException, IOException, GitAPIException {
		final Build build = new Build(Paths.get(args[0]), args[1], HCollection.asList(Arrays.copyOfRange(args, 2, args.length)));
		build.build();
	}

	@Getter(AccessLevel.PROTECTED)
	protected final IMaven maven = new Proxifier().generate(new ProcessBuilderRunner(), IMaven.class);

	protected final Path root;

	protected final String branch;

	protected final List<String> targets;

	public void build() throws IOException, GitAPIException {
		HLog.getLogControl().setLogLevel(Level.INFO);
		log.info("Building: {}", getTargets());

		// Load information about all the projects
		log.info("Loading project information");
		final Map<String, BuildProject> projects = new Projects(getRoot().resolve("pom.xml")).getProjects().stream().map(BuildProject::new).collect(Collectors.toMap(BuildProject::getName, IFunction1.identity()));
		try {
			// Print a list of the public projects
			final List<BuildProject> publicProjects = projects.values().stream().filter(project -> Project.Protection.Public.equals(project.getProject().getProtection())).collect(Collectors.toList());
			log.info("Public projects: {}", publicProjects.stream().map(BuildProject::getName).collect(Collectors.joining(", ")));

			// Check for uncommitted changes in any project, and fail
			final List<BuildProject> dirty = projects.values().stream().filter(project -> {
				try {
					return !project.getGit().status().call().isClean();
				} catch (NoWorkTreeException | GitAPIException e) {
					throw new RuntimeException(String.format("Failure while attempting to check whether %1$s is dirty!", project.getName()), e);
				}
			}).collect(Collectors.toList());
			if (!dirty.isEmpty()) throw new IllegalStateException(String.format("One or more projects were dirty (%1$s), please commit changes and try again!", dirty.stream().map(BuildProject::getName).collect(Collectors.joining(", "))));

			// Compute the order in which to build the public projects
			log.info("Planning builder order");
			final List<String> order = computeBuildOrder(publicProjects);
			log.info("Build order: {}", order);

			// Prepare all the releases
			for (String name : order) {
				log.info("Preparing {}", name);
				final BuildProject project = projects.get(name);

				// Create and switch to the release branch if needed
				final String current = project.getGit().getRepository().getBranch();
				if (!current.equals(branch)) project.getGit().checkout().setCreateBranch(true).setName(branch).call();

				// Prepare the project (stream stdio to the console)
				getMaven().releasePrepare(project.getDirectory());

				// Check out the recent tag using jgit
				final String tag;
				try (final InputStream stream = Files.newInputStream(project.getDirectory().resolve("release.properties"))) {
					final Properties properties = new Properties();
					properties.load(stream);
					tag = properties.getProperty("scm.tag");
				}
				project.getGit().checkout().setStartPoint(tag).call();

				// Maven install (stream stdio to the console) the newly created release version
				getMaven().install(project.getDirectory());

				// Update everyone who consumes this project (including the private consumers!) to the new version (and commit)
				// TODO: update versions
				// versions:update-parent versions:use-latest-releases versions:update-properties -Psandbox,private -Dincludes=com.g2forge.alexandria:*
				// TODO: commit any modified downstream projects, maybe just marking them dirty so I can commit if they're in the release plan
			}

			// Perform the releases
			for (String project : order) {
				log.info("Releasing {}", project);
				getMaven().releasePerform(getRoot().resolve(project));
				// TODO: Test this
			}

			// Cleanup
			for (String project : order) {
				log.info("Restarting development of {}", project);
				final Path directory = getRoot().resolve(project);

				// Remove the maven temporary install of the new release version
				// TODO

				// Check out the branch heads
				// TODO

				// Maven install (stream stdio to the console) the new development versions
				getMaven().install(directory);
				// TODO: Test this
			}

			{
				log.info("Updating downstream projects");
				// Update all the downstreams to new snapshot versions
				// TODO

				// See the update to release, and change the following: versions:use-latest-snapshots -DallowSnapshots=true
				// TODO

				// Commit any of them which were modified
				// TODO
			}
		} finally {
			HIO.closeAll(projects.values());
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
}
