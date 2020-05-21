package com.g2forge.bulldozer.build;

import java.io.BufferedWriter;
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

import org.slf4j.event.Level;

import com.g2forge.alexandria.command.command.IConstructorCommand;
import com.g2forge.alexandria.command.command.IStandardCommand;
import com.g2forge.alexandria.command.exit.IExit;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.bulldozer.build.model.BulldozerProject;
import com.g2forge.bulldozer.build.model.Context;
import com.g2forge.bulldozer.build.model.maven.MavenProject;
import com.g2forge.bulldozer.build.model.maven.MavenProject.Protection;
import com.g2forge.enigma.diagram.plantuml.convert.PUMLRenderer;
import com.g2forge.enigma.diagram.plantuml.model.PUMLContent;
import com.g2forge.enigma.diagram.plantuml.model.klass.PUMLClass;
import com.g2forge.enigma.diagram.plantuml.model.klass.PUMLClassDiagram;
import com.g2forge.enigma.diagram.plantuml.model.klass.PUMLRelation;
import com.g2forge.enigma.diagram.plantuml.model.style.PUMLControl;
import com.g2forge.enigma.diagram.plantuml.model.style.StringPUMLColor;
import com.g2forge.enigma.diagram.plantuml.model.style.TransparentPUMLColor;
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
import com.g2forge.enigma.document.convert.WikitextParser;
import com.g2forge.enigma.document.convert.md.MDRenderer;

import lombok.Data;
import net.sourceforge.plantuml.FileFormat;

@Data
public class Catalog implements IConstructorCommand {
	public static final IStandardCommand COMMAND_FACTORY = IStandardCommand.of(invocation -> new Catalog(new Context<BulldozerProject>(BulldozerProject::new, Paths.get(invocation.getArguments().get(0)))));

	public static void main(String[] args) throws Throwable {
		IStandardCommand.main(args, COMMAND_FACTORY);
	}

	protected final Context<BulldozerProject> context;

	@Override
	public IExit invoke() throws Throwable {
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

						dependencies.uclass(PUMLClass.builder().name(project.getName()).stereotypeSpot('P', new StringPUMLColor("LightBlue")).build());
						project.getDependencies().getImmediate().keySet().stream().filter(inclusive::contains).forEach(d -> dependencies.relation(PUMLRelation.builder().left(d).type(PUMLRelation.Type.Arrow).right(project.getName()).back(true).vertical(true).build()));
					}

					// Render the diagram
					final Path images = getContext().getRoot().resolve("images");
					Files.createDirectories(images);
					final PUMLContent.PUMLContentBuilder builder = PUMLContent.builder().diagram(dependencies.build());
					builder.control(PUMLControl.builder().shadowing(false).dpi(150).background(TransparentPUMLColor.create()).build());
					final Path png = new PUMLRenderer().toFile(builder.build(), images.resolve("dependencies_" + protection.name().toLowerCase()), FileFormat.PNG);

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
			final Path readme = getContext().getRoot().resolve("README.md");
			final Block input = WikitextParser.getMarkdown().parse(readme);

			final List<IBlock> contents = new ArrayList<>(input.getContents());
			final Section projectsSection = Section.builder().title(new Text("Projects")).body(docBuilder.build()).build();
			int i = 0;
			for (; i < contents.size(); i++) {
				final IBlock block = contents.get(i);
				if (!(block instanceof Section)) continue;
				final Section section = (Section) block;
				if (!(section.getTitle() instanceof Text)) continue;
				if (!"Projects".equals(((Text) section.getTitle()).getText())) continue;

				contents.set(i, projectsSection);
				break;
			}
			if (i >= contents.size()) contents.add(projectsSection);

			final Block output = Block.builder().type(Block.Type.Document).contents(contents).build();
			try (final BufferedWriter writer = Files.newBufferedWriter(readme)) {
				writer.write(new MDRenderer().render(output));
			}
		}
		return SUCCESS;
	}
}
