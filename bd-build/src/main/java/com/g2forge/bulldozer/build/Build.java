package com.g2forge.bulldozer.build;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.g2forge.alexandria.data.graph.HGraph;
import com.g2forge.alexandria.java.associative.cache.Cache;
import com.g2forge.alexandria.java.associative.cache.NeverCacheEvictionPolicy;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.function.IFunction1;
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
	public static void main(String[] args) throws JsonParseException, JsonMappingException, IOException {
		new Build(Paths.get(args[0]), args[1], HCollection.asList(Arrays.copyOfRange(args, 2, args.length))).build();
	}

	@Getter(AccessLevel.PROTECTED)
	protected final IMaven maven = new Proxifier().generate(new ProcessBuilderRunner(), IMaven.class);

	protected final Path root;

	protected final String branch;

	protected final List<String> targets;

	public void build() {
		// Load information about all the projects
		final Projects projects = new Projects(getRoot().resolve("pom.xml"));

		// Load the group information for all the public projects
		final Map<String, String> projectToGroup = projects.getProjects().stream().filter(project -> Project.Protection.Public.equals(project.getProtection())).map(Project::getName).collect(Collectors.toMap(IFunction1.identity(), name -> getMaven().evaluate(root.resolve(name), "project.groupId")));
		log.info("Public projects: {}", projectToGroup.keySet());
		final Map<String, String> groupToProject = projectToGroup.keySet().stream().collect(Collectors.toMap(projectToGroup::get, IFunction1.identity()));

		// Cache the project dependencies, where the dependencies are a map from the project to the version depended on
		final Cache<String, Map<String, String>> projectToDependencies = new Cache<String, Map<String, String>>(project -> {
			log.info("Loading dependencies for {}", project);
			// Run maven dependencies and filter the output down to usable information
			final Map<String, List<Descriptor>> grouped = getMaven().dependencyTree(getRoot().resolve(project), true, projectToGroup.values().stream().map(g -> g + ":*").collect(Collectors.toList())).filter(line -> {
				if (!line.startsWith("[INFO]")) return false;
				for (String publicGroup : projectToGroup.values()) {
					if (line.contains("- " + publicGroup)) return true;
				}
				return false;
			}).map(line -> Descriptor.fromString(line.substring(line.indexOf("- ") + 2))).filter(descriptor -> !descriptor.getGroup().equals(projectToGroup.get(project))).collect(Collectors.groupingBy(Descriptor::getGroup));
			// Extract the per-project version and make sure we only ever depend on one version
			final Map<String, String> versions = new LinkedHashMap<>();
			for (List<Descriptor> descriptors : grouped.values()) {
				final Set<String> groupVersions = descriptors.stream().map(Descriptor::getVersion).collect(Collectors.toSet());
				if (groupVersions.size() > 1) throw new IllegalArgumentException(String.format("Depended on multiple versions of the project \"%1$s\": %2$s", project, groupVersions));
				final String group = descriptors.get(0).getGroup();
				versions.put(groupToProject.get(group), HCollection.getOne(groupVersions));
			}
			log.info("Found dependencies for {}: {}", project, versions);
			return versions;
		}, NeverCacheEvictionPolicy.create());

		final List<String> order = HGraph.toposort(targets, p -> projectToDependencies.apply(p).keySet(), false);
		log.info("Builder order: {}", order);

		// Prepare all the releases
		for (String project : order) {
			final Path directory = getRoot().resolve(project);
			try (final Git git = HGit.createGit(directory, false)) {
				// Create and switch to the release branch if needed
				final String current = git.getRepository().getBranch();
				if (!current.equals(branch)) git.checkout().setCreateBranch(true).setName(branch).call();

				// Prepare the project (stream the output to the console)
				getMaven().releasePrepare(directory);
				// TODO: passthrough the stdio

				// Check out the recent tag using jgit
				// git.checkout().setStartPoint("TAG GOES HERE").call();
				// TODO

				// Maven install
				// getMaven().install(directory);
				// TODO: passthrough the stdio

				// Update everyone who consumes this project (including the private consumers!) to the new version (and commit)
				// TODO: update versions
				// TODO: commit those downstream projects

			}
		}

		// Perform the releases
		for (String project : order) {

		}

		// Cleanup
		for (String project : order) {
			// Check out the branch head
			// Remove the maven temporary install
		}
	}
}
