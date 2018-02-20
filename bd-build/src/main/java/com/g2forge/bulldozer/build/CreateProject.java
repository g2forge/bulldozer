package com.g2forge.bulldozer.build;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.mylyn.wikitext.markdown.MarkdownLanguage;
import org.kohsuke.github.GHCreateRepositoryBuilder;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.slf4j.event.Level;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.alexandria.wizard.CommandLineStringInput;
import com.g2forge.alexandria.wizard.UserStringInput;
import com.g2forge.bulldozer.build.input.InputLoader;
import com.g2forge.bulldozer.build.model.BulldozerCreateProject;
import com.g2forge.bulldozer.build.model.BulldozerProject;
import com.g2forge.bulldozer.build.model.Context;
import com.g2forge.bulldozer.build.model.maven.MavenProject;
import com.g2forge.enigma.document.Block;
import com.g2forge.enigma.document.IBlock;
import com.g2forge.enigma.document.Section;
import com.g2forge.enigma.document.Text;
import com.g2forge.enigma.document.convert.WikitextDocumentBuilder;
import com.g2forge.enigma.document.convert.md.MDRenderer;
import com.g2forge.gearbox.git.GitConfig;
import com.g2forge.gearbox.git.GitIgnore;
import com.g2forge.gearbox.git.HGit;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class CreateProject {
	@RequiredArgsConstructor
	@Getter
	public enum StandardIgnore {
		Project("/.project"),
		Classpath("/.classpath"),
		Settings("/.settings/"),
		Target("/target/"),
		BulldozerTemp("/" + com.g2forge.bulldozer.build.model.BulldozerTemp.BULLDOZER_TEMP),
		Factorypath("/.factorypath");

		public static GitIgnore createIgnore() {
			return new GitIgnore(Stream.of(StandardIgnore.values()).map(StandardIgnore::getFilename).collect(Collectors.toList()));
		}

		protected final String filename;
	}

	@RequiredArgsConstructor
	@Getter
	public enum StandardLabel {
		Master("master", "0e8a16"),
		HOLDAuthor("HOLD: Author", "b60205"),
		HOLDOtherPR("HOLD: Other PR", "b60205");

		protected final String text;

		protected final String color;
	}

	public static void main(String[] args) throws JsonParseException, JsonMappingException, IOException, GitAPIException {
		final String issue = new CommandLineStringInput(args, 1).fallback(new UserStringInput("Issue", true)).get();
		final CreateProject create = new CreateProject(new Context<BulldozerProject>(BulldozerProject::new, Paths.get(args[0])), issue);
		create.create();
	}

	protected final Context<BulldozerProject> context;

	protected final String issue;

	public void create() throws IOException, GitAPIException {
		HLog.getLogControl().setLogLevel(Level.INFO);
		final BulldozerCreateProject create = new InputLoader().load(BulldozerCreateProject.createInputType(getContext()));

		final GHOrganization organization = getContext().getGithub().getOrganization("g2forge");
		final String repositoryName = create.getName().toLowerCase();

		final GHRepository repository;
		{ // Find or create the repository
			log.info(String.format("Checking for %1$s/%2$s", organization.getLogin(), repositoryName));
			final GHRepository found = organization.getRepository(repositoryName);
			if (found != null) {
				repository = found;
				log.info(String.format("Found %1$s/%2$s", organization.getLogin(), repositoryName));
			} else {
				log.info(String.format("Creating %1$s/%2$s", organization.getLogin(), repositoryName));
				final GHCreateRepositoryBuilder builder = organization.createRepository(repositoryName);
				builder.description(create.getDescription()).private_(!MavenProject.Protection.Public.equals(create.getProtection()));
				builder.issues(true).wiki(false).autoInit(true).licenseTemplate("apache-2.0");
				repository = builder.allowRebaseMerge(false).allowSquashMerge(false).allowMergeCommit(true).create();
				log.info(String.format("Created %1$s/%2$s", organization.getLogin(), repositoryName));
			}
		}

		{ // Set up the labels
			final Map<String, GHLabel> labels = new HashMap<>(StreamSupport.stream(repository.listLabels().spliterator(), false).collect(Collectors.toMap(GHLabel::getName, IFunction1.identity())));
			for (StandardLabel label : StandardLabel.values()) {
				if (labels.remove(label.getText()) == null) {
					log.info(String.format("Creating standard label: %1$s", label.getText()));
					repository.createLabel(label.getText(), label.getColor());
				}
			}
			if (!labels.values().isEmpty()) {
				log.info("Removing non-standard labels");
				for (GHLabel label : labels.values()) {
					label.delete();
				}
			}
		}

		{ // Set up the milestones
			final List<GHMilestone> milestones = StreamSupport.stream(repository.listMilestones(GHIssueState.OPEN).spliterator(), false).collect(Collectors.toList());
			final String version = create.getVersion().toReleaseVersion().toString();
			if (!milestones.stream().filter(m -> m.getTitle().startsWith(version)).findAny().isPresent()) {
				log.info(String.format("Creating milestone %1$s", version));
				repository.createMilestone(version, null);
			}
		}

		final GHMyself myself = getContext().getGithub().getMyself();
		final GHRepository fork;
		{ // Fork the repository to the creators account
			log.info(String.format("Checking for %1$s/%2$s", myself.getLogin(), repositoryName));
			final GHRepository found = myself.getRepository(repositoryName);
			if (found != null) {
				fork = found;
				log.info(String.format("Found %1$s/%2$s", myself.getLogin(), repositoryName));
			} else {
				log.info(String.format("Forking %1$s/%3$s to %2$s/%3$s", organization.getLogin(), myself.getLogin(), repositoryName));
				fork = repository.fork();
				log.info(String.format("Forked %1$s/%3$s to %2$s/%3$s", organization.getLogin(), myself.getLogin(), repositoryName));
			}
		}

		final Path directory = getContext().getRoot().resolve(repositoryName);
		if (!Files.exists(directory)) {
			log.info(String.format("Cloning %1$s/%2$s into %3$s", organization.getLogin(), repositoryName, directory));
			Git.cloneRepository().setTransportConfigCallback(getContext().getTransportConfig()).setDirectory(directory.toFile()).setURI(repository.getSshUrl()).call();
			log.info(String.format("Cloned %1$s/%2$s into %3$s", organization.getLogin(), repositoryName, directory));
		}

		final Git git = HGit.createGit(directory, false);
		if (!git.getRepository().getRemoteNames().contains(myself.getLogin())) {
			log.info(String.format("Creating remote for %1$s -> %2$s", myself.getLogin(), fork.getSshUrl()));
			final GitConfig config = new GitConfig(git);
			config.getRemote(myself.getLogin()).add(fork.getSshUrl()).save();
			git.pull().setTransportConfigCallback(context.getTransportConfig()).setRemote(myself.getLogin()).call();
		}

		// Create the branch for out modifications
		final String branch = getIssue() + "-Start" + create.getName();
		if (!HGit.isBranch(git, branch)) {
			log.info(String.format("Creating branch %1$s", branch));
			git.checkout().setCreateBranch(true).setName(branch).call();
		}

		{ // Rewrite the README.md
			log.info("Generating README");
			final Block input;
			final Path readme = directory.resolve("README.md");
			try (final BufferedReader reader = Files.newBufferedReader(readme)) {
				input = WikitextDocumentBuilder.parse(new MarkdownLanguage(), reader);
			}
			final List<IBlock> contents = new ArrayList<>(input.getContents());
			contents.set(0, Section.builder().title(new Text(create.getName())).body(new Text(create.getDescription())).build());
			final Block output = Block.builder().type(Block.Type.Document).contents(contents).build();
			try (final BufferedWriter writer = Files.newBufferedWriter(readme)) {
				writer.write(new MDRenderer().render(output));
			}
			commit(git, getIssue() + " Start " + create.getName(), readme.getFileName().toString());
		}

		{ // Create the directory structure & git ignores
			log.info("Generating directory structure & git ignores");
			final String xxProjectName = create.getPrefix() + "-project";
			final Path xxProjectPath = directory.resolve(xxProjectName);
			Files.createDirectories(xxProjectPath);

			// GitIgnore.load(directory);
			// TODO: Read in the ignore if it exists, add the new entries if they don't exist
			final GitIgnore ignore = StandardIgnore.createIgnore();
			ignore.store(directory);
			ignore.store(xxProjectPath);
			commit(git, getIssue() + " Create standard ignores", Constants.GITIGNORE_FILENAME, xxProjectName + "/" + Constants.GITIGNORE_FILENAME);

			// Create the POM files
			// TODO: root
			// TODO: xx-project
		}

		// Modify the meta-project pom.xml file
		// TODO: !!

		// Note that we do not push the branch or open PRs, there is another command for that
	}

	public static void commit(Git git, String message, String... files) throws NoWorkTreeException, GitAPIException {
		final StatusCommand status = git.status();
		for (String file : files) {
			status.addPath(file);
		}

		final Status result = status.call();
		final List<String> collection = HCollection.asList(files);
		if (!HCollection.intersection(result.getUncommittedChanges(), collection).isEmpty() || !HCollection.intersection(result.getUntracked(), collection).isEmpty()) {
			final AddCommand add = git.add();
			for (String file : files) {
				add.addFilepattern(file);
			}
			add.call();

			git.commit().setMessage(message).call();
			log.info(String.format("Comitting: %1$s", message));
		}
	}
}
