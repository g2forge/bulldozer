package com.g2forge.bulldozer.build;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.event.Level;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.bulldozer.build.github.GitHubRepositoryID;
import com.g2forge.bulldozer.build.model.BulldozerProject;
import com.g2forge.bulldozer.build.model.Context;
import com.g2forge.bulldozer.build.model.maven.MavenProject;
import com.g2forge.enigma.document.Block;
import com.g2forge.enigma.document.DocList;
import com.g2forge.enigma.document.Link;
import com.g2forge.enigma.document.Section;
import com.g2forge.enigma.document.Span;
import com.g2forge.enigma.document.Span.SpanBuilder;
import com.g2forge.enigma.document.Text;
import com.g2forge.enigma.document.convert.md.MDRenderer;
import com.g2forge.gearbox.git.GitConfig;

import lombok.Data;

@Data
public class Catalog {

	public static void main(String[] args) throws JsonParseException, JsonMappingException, IOException, GitAPIException {
		final Catalog catalog = new Catalog(new Context(Paths.get(args[0])));
		catalog.catalog();
	}

	protected final Context context;

	public void catalog() throws IOException, GitAPIException {
		HLog.getLogControl().setLogLevel(Level.INFO);
		final Block.BlockBuilder projectsBuilder = Block.builder();

		final Map<String, BulldozerProject> projects = getContext().loadProjects(BulldozerProject::new);

		// Create a list of the project names and sort it alphabetically
		final List<String> ordered = new ArrayList<>(projects.keySet());
		Collections.sort(ordered);

		// Loop over all the different protection profiles
		for (MavenProject.Protection protection : MavenProject.Protection.values()) {
			// Build a list of projects in this protection profile
			final DocList.DocListBuilder listBuilder = DocList.builder().marker(DocList.Marker.Ordered);
			for (String name : ordered) {
				final BulldozerProject project = projects.get(name);
				// This is where we skip projects not in this protection profile (could use streaming groupby in the future)
				if (!protection.equals(project.getProject().getProtection())) continue;

				final SpanBuilder item = Span.builder();

				// Link to the github page for the repository
				final GitHubRepositoryID repositoryID = GitHubRepositoryID.fromURL(new GitConfig(project.getGit()).getOrigin().getURL());
				item.content(new Link(repositoryID.toWebsiteURL(), new Text(project.getPom().getName())));

				// Pull the description from the pom
				final String description = project.getPom().getDescription();
				if (description != null) item.content(new Text(" - " + description));

				listBuilder.item(item.build());
			}
			// If we found any projects in this protection profile, then generate a section with the list of projects
			final DocList list = listBuilder.build();
			if (!list.getItems().isEmpty()) projectsBuilder.content(Section.builder().title(new Text(protection.name())).body(list).build());
		}

		System.out.println(new MDRenderer().render(Section.builder().title(new Text("Projects")).body(projectsBuilder.build()).build()));
	}
}
