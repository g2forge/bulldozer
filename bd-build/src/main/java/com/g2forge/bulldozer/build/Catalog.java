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
import com.g2forge.enigma.document.DocList;
import com.g2forge.enigma.document.Link;
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
		final Map<String, BulldozerProject> projects = getContext().loadProjects(BulldozerProject::new);

		final DocList.DocListBuilder list = DocList.builder().marker(DocList.Marker.Ordered);
		final List<String> ordered = new ArrayList<>(projects.keySet());
		Collections.sort(ordered);
		for (String name : ordered) {
			final SpanBuilder item = Span.builder();

			final BulldozerProject project = projects.get(name);

			final GitHubRepositoryID repositoryID = GitHubRepositoryID.fromURL(new GitConfig(project.getGit()).getOrigin().getURL());
			item.content(new Link(repositoryID.toWebsiteURL(), new Text(project.getPom().getName())));

			final String description = project.getPom().getDescription();
			if (description != null) item.content(new Text(" - " + description));

			list.item(item.build());
		}
		System.out.println(new MDRenderer().render(list.build()));
	}
}
