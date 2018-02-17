package com.g2forge.bulldozer.build;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.mylyn.wikitext.markdown.MarkdownLanguage;
import org.slf4j.event.Level;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.bulldozer.build.model.BulldozerProject;
import com.g2forge.bulldozer.build.model.Context;
import com.g2forge.bulldozer.build.model.maven.MavenProject;
import com.g2forge.bulldozer.build.model.maven.MavenProject.Protection;
import com.g2forge.enigma.diagram.PUMLContent;
import com.g2forge.enigma.diagram.PUMLControl;
import com.g2forge.enigma.diagram.klass.PUMLClass;
import com.g2forge.enigma.diagram.klass.PUMLClassDiagram;
import com.g2forge.enigma.diagram.klass.PUMLClassName;
import com.g2forge.enigma.diagram.klass.PUMLRelation;
import com.g2forge.enigma.document.Block;
import com.g2forge.enigma.document.DocList;
import com.g2forge.enigma.document.IBlock;
import com.g2forge.enigma.document.Image;
import com.g2forge.enigma.document.Link;
import com.g2forge.enigma.document.Section;
import com.g2forge.enigma.document.Section.SectionBuilder;
import com.g2forge.enigma.document.Span;
import com.g2forge.enigma.document.Span.SpanBuilder;
import com.g2forge.enigma.document.Text;
import com.g2forge.enigma.document.convert.WikitextDocumentBuilder;
import com.g2forge.enigma.document.convert.md.MDRenderer;

import lombok.Data;
import net.sourceforge.plantuml.FileFormat;

@Data
public class Catalog {
	public static void main(String[] args) throws JsonParseException, JsonMappingException, IOException, GitAPIException {
		final Catalog catalog = new Catalog(new Context<BulldozerProject>(BulldozerProject::new, Paths.get(args[0])));
		catalog.catalog();
	}

	protected final Context<BulldozerProject> context;

	public void catalog() throws IOException, GitAPIException {
		HLog.getLogControl().setLogLevel(Level.INFO);
		final Block.BlockBuilder docBuilder = Block.builder().type(Block.Type.Block);
		final Map<String, BulldozerProject> projects = getContext().getProjects();

		// Create a list of the project names and sort it alphabetically
		final List<String> ordered = new ArrayList<>(projects.keySet());
		Collections.sort(ordered);

		// Create an inclusive and exclusive list of projects
		final Map<Protection, Set<String>> protectionProfilesExclusive = projects.values().stream().collect(Collectors.groupingBy(p -> p.getProject().getProtection(), Collectors.mapping(p -> p.getName(), Collectors.toSet())));
		final Map<Protection, Set<String>> protectionProfilesInclusive = new HashMap<>();
		for (MavenProject.Protection protection : MavenProject.Protection.values()) {
			final Set<String> names = new HashSet<>();
			for (int i = 0; i <= protection.ordinal(); i++) {
				names.addAll(protectionProfilesExclusive.get(MavenProject.Protection.values()[i]));
			}
			protectionProfilesInclusive.put(protection, names);
		}

		// Loop over all the different protection profiles
		for (MavenProject.Protection protection : MavenProject.Protection.values()) {
			// Build a list of projects in this protection profile
			final DocList.DocListBuilder listBuilder = DocList.builder().marker(DocList.Marker.Ordered);
			for (String name : ordered.stream().filter(protectionProfilesExclusive.get(protection)::contains).collect(Collectors.toList())) {
				final BulldozerProject project = projects.get(name);
				final SpanBuilder item = Span.builder();

				item.content(new Link(project.getGithubMaster().toWebsiteURL(), new Text(project.getPom().getName())));

				// If the project is public, add a link to the maven search
				if (MavenProject.Protection.Public.equals(protection)) {
					item.content(new Text(", ")).content(new Link("https://mvnrepository.com/artifact/" + project.getGroup(), new Text("Maven")));
				}

				// Pull the description from the pom
				final String description = project.getPom().getDescription();
				if (description != null) item.content(new Text(" - " + description));

				listBuilder.item(item.build());
			}
			// If we found any projects in this protection profile, then generate a section with the list of projects
			final DocList list = listBuilder.build();
			if (!list.getItems().isEmpty()) {
				final SectionBuilder section = Section.builder().title(new Text(protection.name()));

				// Generate the dependency diagram
				if (MavenProject.Protection.Sandbox.compareTo(protection) > 0) {
					final PUMLClassDiagram.PUMLClassDiagramBuilder dependencies = PUMLClassDiagram.builder();
					// Add the project to the dependencies diagram for this protection if it's more public that the protection
					final Set<String> inclusive = ordered.stream().filter(protectionProfilesInclusive.get(protection)::contains).collect(Collectors.toCollection(LinkedHashSet::new));
					for (String name : inclusive) {
						final BulldozerProject project = projects.get(name);

						final PUMLClassName umlName = new PUMLClassName(project.getName());
						dependencies.uclass(PUMLClass.builder().name(umlName).stereotype("(P,LightBlue)").build());
						project.getDependencies().getImmediate().keySet().stream().filter(inclusive::contains).forEach(d -> dependencies.relation(PUMLRelation.builder().left(new PUMLClassName(d)).type(PUMLRelation.Type.Arrow).right(umlName).build()));
					}

					// Render the diagram
					final Path images = getContext().getRoot().resolve("images");
					Files.createDirectories(images);
					final PUMLContent.PUMLContentBuilder builder = PUMLContent.builder().diagram(dependencies.build());
					builder.control(PUMLControl.builder().shadowing(false).dpi(150).background(PUMLControl.Color.Transparent).build());
					final Path png = builder.build().toFile(images.resolve("dependencies_" + protection.name().toLowerCase()), FileFormat.PNG);

					// Add the diagram into the MD
					final Block.BlockBuilder block = Block.builder().type(Block.Type.Block);
					block.content(Image.builder().alt(protection.name().toLowerCase() + " dependencies").url("images/" + png.getFileName().toString()).build());
					block.content(list);
					section.body(block.build());
				} else section.body(list);

				docBuilder.content(section.build());
			}
		}

		{ // Rewrite the README.md
			final Block input;
			final Path readme = getContext().getRoot().resolve("README.md");
			try (final BufferedReader reader = Files.newBufferedReader(readme)) {
				input = WikitextDocumentBuilder.parse(new MarkdownLanguage(), reader);
			}
			final List<IBlock> contents = new ArrayList<>(input.getContents());
			contents.set(1, Section.builder().title(new Text("Projects")).body(docBuilder.build()).build());
			final Block output = Block.builder().type(Block.Type.Document).contents(contents).build();
			try (final BufferedWriter writer = Files.newBufferedWriter(readme)) {
				writer.write(new MDRenderer().render(output));
			}
		}
	}
}
