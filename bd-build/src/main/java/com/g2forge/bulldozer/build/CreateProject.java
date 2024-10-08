package com.g2forge.bulldozer.build;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Constants;
import org.kohsuke.github.GHCreateRepositoryBuilder;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.slf4j.event.Level;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.g2forge.alexandria.command.command.IConstructorCommand;
import com.g2forge.alexandria.command.command.IStandardCommand;
import com.g2forge.alexandria.command.exit.IExit;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.alexandria.java.platform.PathSpec;
import com.g2forge.alexandria.java.project.HProject;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.alexandria.wizard.CommandLineStringInput;
import com.g2forge.alexandria.wizard.UserStringInput;
import com.g2forge.bulldozer.build.github.GitHubRepositoryID;
import com.g2forge.bulldozer.build.input.InputLoader;
import com.g2forge.bulldozer.build.maven.Descriptor;
import com.g2forge.bulldozer.build.maven.POM;
import com.g2forge.bulldozer.build.maven.Parent;
import com.g2forge.bulldozer.build.maven.Profile;
import com.g2forge.bulldozer.build.maven.Profile.ProfileBuilder;
import com.g2forge.bulldozer.build.maven.Repository;
import com.g2forge.bulldozer.build.maven.Repository.Policies;
import com.g2forge.bulldozer.build.maven.build.Build;
import com.g2forge.bulldozer.build.maven.build.plugin.VersionsPlugin;
import com.g2forge.bulldozer.build.maven.build.plugin.VersionsPlugin.Configuration.ConfigurationBuilder;
import com.g2forge.bulldozer.build.maven.distribution.DistributionRepository;
import com.g2forge.bulldozer.build.maven.distribution.DistributionSnapshotRepository;
import com.g2forge.bulldozer.build.maven.metadata.Developer;
import com.g2forge.bulldozer.build.maven.metadata.License;
import com.g2forge.bulldozer.build.maven.metadata.SCM;
import com.g2forge.bulldozer.build.maven.metadata.SCM.SCMBuilder;
import com.g2forge.bulldozer.build.maven.settings.Server;
import com.g2forge.bulldozer.build.maven.settings.Settings;
import com.g2forge.bulldozer.build.model.BulldozerCreateProject;
import com.g2forge.bulldozer.build.model.BulldozerProject;
import com.g2forge.bulldozer.build.model.Context;
import com.g2forge.bulldozer.build.model.maven.MavenProject;
import com.g2forge.enigma.document.convert.WikitextParser;
import com.g2forge.enigma.document.convert.md.MDRenderer;
import com.g2forge.enigma.document.model.Block;
import com.g2forge.enigma.document.model.IBlock;
import com.g2forge.enigma.document.model.Section;
import com.g2forge.enigma.document.model.Text;
import com.g2forge.gearbox.git.GitConfig;
import com.g2forge.gearbox.git.GitIgnore;
import com.g2forge.gearbox.git.HGit;
import com.g2forge.gearbox.github.actions.HGHActions;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class CreateProject implements IConstructorCommand {
	@RequiredArgsConstructor
	@Getter
	public enum StandardIgnore {
		Project("/.project"),
		Classpath("/.classpath"),
		Settings("/.settings/"),
		Target("/target/"),
		BulldozerTemp("/" + com.g2forge.bulldozer.build.model.BulldozerTemp.BULLDOZER_TEMP),
		BulldozerState("/" + Release.State.BULLDOZER_STATE),
		Factorypath("/.factorypath");

		public static GitIgnore createIgnore() {
			return new GitIgnore(Stream.of(StandardIgnore.values()).map(StandardIgnore::getFilename).collect(Collectors.toList()));
		}

		public static GitIgnore ensure(GitIgnore input) {
			final Collection<String> lines = new LinkedHashSet<>(input.getLines());
			lines.addAll(createIgnore().getLines());
			return new GitIgnore(new ArrayList<>(lines));
		}

		protected final String filename;
	}

	@RequiredArgsConstructor
	@Getter
	public enum StandardLabel {
		Main("main", "0e8a16"),
		HOLDAuthor("HOLD: Author", "b60205"),
		HOLDOtherPR("HOLD: Other PR", "b60205");

		protected final String text;

		protected final String color;
	}

	public static final IStandardCommand COMMAND_FACTORY = IStandardCommand.of(invocation -> {
		final String issue = new CommandLineStringInput(invocation, 1).fallback(new UserStringInput("Issue", true)).get();
		return new CreateProject(new Context<BulldozerProject>(BulldozerProject::new, Paths.get(invocation.getArguments().get(0))), issue);
	});

	protected static final String GITHUB_ACTOR = "GITHUB_ACTOR";

	protected static final String GITHUB_TOKEN = "GITHUB_TOKEN";

	protected static final String PACKAGESREAD_USERNAME = "PACKAGESREAD_USERNAME";

	protected static final String PACKAGESREAD_TOKEN = "PACKAGESREAD_TOKEN";

	protected static final String MAVEN_REPOSITORYID_DEPLOYMENT = "github";

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

	protected static String getMavenRepsitoryIDOrganization(String organization) {
		return String.format("github-", organization);
	}

	public static void main(String[] args) throws Throwable {
		IStandardCommand.main(args, COMMAND_FACTORY);
	}

	public static void updateMultiModulePOM(Path path, MavenProject.Protection protection, String name) throws SAXException, IOException, ParserConfigurationException, XPathExpressionException, TransformerFactoryConfigurationError, TransformerException {
		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile());

		final XPath xpath = XPathFactory.newInstance().newXPath();
		final String expression = MavenProject.Protection.Public.equals(protection) ? "/project/modules" : String.format("/project/profiles/profile[id='%1$s']/modules", protection.name().toLowerCase());
		final Node modules = (Node) xpath.compile(expression).evaluate(doc, XPathConstants.NODE);

		final NodeList children = modules.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			final Node child = children.item(i);
			if (child.getTextContent().equals(name)) return;
		}
		final Element module = doc.createElement("module");
		modules.appendChild(module);
		module.setTextContent(name);

		final Transformer transformer = TransformerFactory.newInstance().newTransformer();
		final DocumentType docType = doc.getDoctype();
		if (docType != null) {
			final String publicID = docType.getPublicId();
			if (publicID != null) transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, publicID);
			transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, docType.getSystemId());
		}
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		transformer.transform(new DOMSource(doc), new StreamResult(buffer));

		try (final PrintStream output = new PrintStream(Files.newOutputStream(path));
			final BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buffer.toByteArray())))) {
			while (true) {
				final String line = reader.readLine();
				if (line == null) break;
				output.println(line.replace("    ", "\t"));
			}
		}
	}

	protected final Context<BulldozerProject> context;

	protected final String issue;

	@Override
	public IExit invoke() throws Throwable {
		HLog.getLogControl().setLogLevel(Level.INFO);
		final BulldozerCreateProject create = new InputLoader().load(BulldozerCreateProject.createInputType(getContext()));

		final Path rootDirectory = getContext().getRoot();
		final Git rootGit = HGit.createGit(rootDirectory, false);
		final String organizationName = GitHubRepositoryID.fromURL(new GitConfig(rootGit).getOrigin().getURL()).getOrganization();
		final String rootProject = getContext().getRootProject();

		final GHOrganization organization = getContext().getGithub().getOrganization(organizationName);
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
				builder.issues(true).wiki(false).autoInit(true).licenseTemplate(create.getLicense().getGithub());
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

		final Path directory = rootDirectory.resolve(repositoryName);
		if (!Files.exists(directory)) {
			log.info(String.format("Cloning %1$s/%2$s into %3$s", organization.getLogin(), repositoryName, directory));
			Git.cloneRepository().setTransportConfigCallback(getContext().getTransportConfig()).setDirectory(directory.toFile()).setURI(repository.getSshUrl()).call().close();
			log.info(String.format("Cloned %1$s/%2$s into %3$s", organization.getLogin(), repositoryName, directory));
		}

		final Git git = HGit.createGit(directory, false);
		if (!git.getRepository().getRemoteNames().contains(myself.getLogin())) {
			log.info(String.format("Creating remote for %1$s -> %2$s", myself.getLogin(), fork.getSshUrl()));
			HGit.addAndPullRemote(git, myself.getLogin(), remote -> remote.add(fork.getSshUrl()), context.getTransportConfig());
		}

		// Create the branch for out modifications
		final String branch = getIssue() + "-Start" + create.getName();
		if (!HGit.isBranch(git, branch)) {
			log.info(String.format("Creating branch %1$s", branch));
			git.checkout().setCreateBranch(true).setName(branch).call();
		}

		{ // Rewrite the README.md
			log.info("Generating README");
			final Path readme = directory.resolve("README.md");
			final Block input = WikitextParser.getMarkdown().parse(readme);

			final List<IBlock> contents = new ArrayList<>(input.getContents());
			contents.set(0, Section.builder().title(new Text(create.getName())).body(new Text(create.getDescription())).build());
			final Block output = Block.builder().type(Block.Type.Document).contents(contents).build();
			try (final BufferedWriter writer = Files.newBufferedWriter(readme)) {
				writer.write(new MDRenderer().render(output));
			}
			commit(git, getIssue() + " Start " + create.getName(), readme.getFileName().toString());
		}

		{
			// Create the directory structure
			log.info("Updating directory structure");
			final String projectName = create.getPrefix() + "-project";
			final Path projectDirectory = directory.resolve(projectName);
			Files.createDirectories(projectDirectory);

			// Create and commit the git ignores
			log.info("Updating git ignores");
			StandardIgnore.ensure(GitIgnore.load(directory)).store(directory);
			StandardIgnore.ensure(GitIgnore.load(projectDirectory)).store(projectDirectory);
			commit(git, getIssue() + " Create standard ignores", Constants.GITIGNORE_FILENAME, projectName + "/" + Constants.GITIGNORE_FILENAME);

			// Create the POM files
			final POM projectPOM;
			{ // Create the project POM
				final POM.POMBuilder pom = POM.builder();
				pom.modelVersion("4.0.0");

				pom.groupId(getContext().getProjects().get(rootProject).getGroup() + "." + repositoryName).artifactId(projectName).version(create.getVersion().toString()).packaging("pom");

				final BulldozerProject parentBulldozerProject;
				final Parent parentProjectParent;
				if (create.getParent() != null) {
					parentBulldozerProject = getContext().getProjects().get(create.getParent());
					parentProjectParent = parentBulldozerProject.getPom().getParent();
					final Path relativePath = projectDirectory.relativize(parentBulldozerProject.getDirectory().resolve(Paths.get(parentProjectParent.getRelativePath()).getParent())).resolve(HProject.POM);
					pom.parent(Parent.builder().groupId(parentProjectParent.getGroupId()).artifactId(parentProjectParent.getArtifactId()).version(parentProjectParent.getVersion()).relativePath(PathSpec.UNIX.canonizePath(relativePath.toString())).build());
					pom.property(create.getParent() + ".version", parentBulldozerProject.getVersion());
				} else {
					parentBulldozerProject = null;
					parentProjectParent = null;
				}

				for (String dependency : create.getDependencies()) {
					pom.property(dependency + ".version", getContext().getProjects().get(dependency).getVersion());
				}
				pom.property(repositoryName + ".organization", organization.getLogin());
				pom.property(repositoryName + ".name", create.getName());
				pom.property(repositoryName + ".repository", repositoryName);

				pom.name(create.getName() + " Project").description("Parent project for " + create.getName() + ".");
				pom.url(String.format("https://github.com/${%1$s.organization}/${%1$s.repository}/tree/${project.version}/${project.artifactId}", repositoryName));
				pom.developer(Developer.builder().name(myself.getName()).email(myself.getEmail()).organization(organization.getName()).organizationUrl(organization.getBlog()).build());
				pom.license(License.builder().name(create.getLicense().getMaven()).url(String.format("https://github.com/${%1$s.organization}/${%1$s.repository}/blob/${project.version}/LICENSE", repositoryName)).build());
				{
					final SCMBuilder scm = SCM.builder();
					scm.connection(String.format("scm:git:git://github.com/${%1$s.organization}/${%1$s.repository}.git", repositoryName));
					scm.developerConnection(String.format("scm:git:ssh://github.com:${%1$s.organization}/${%1$s.repository}.git", repositoryName));
					scm.url(String.format("http://github.com/${%1$s.organization}/${%1$s.repository}/tree/${project.version}", repositoryName));
					pom.scm(scm.build());
				}

				{ // Create the versions plugin configuration
					final ConfigurationBuilder builder = VersionsPlugin.Configuration.builder();
					if (create.getParent() != null) builder.property(VersionsPlugin.Property.builder().name(create.getParent() + ".version").dependency(Descriptor.builder().groupId(parentProjectParent.getGroupId()).artifactId(parentBulldozerProject.getPom().getArtifactId()).build()).build());
					for (String dependency : create.getDependencies()) {
						final POM dependencyPOM = getContext().getProjects().get(dependency).getPom();
						builder.property(VersionsPlugin.Property.builder().name(dependency + ".version").dependency(Descriptor.builder().groupId(dependencyPOM.getGroupId()).artifactId(dependencyPOM.getArtifactId()).build()).build());
					}
					pom.build(Build.builder().plugin(VersionsPlugin.builder().version("2.5").configuration(builder.build()).build()).build());
				}

				{ // Create the "release-snapshots" profile
					final ProfileBuilder profile = Profile.builder().id("release-snapshot");

					{// Distribution Management
						final String name = String.format("GitHub %1$s Apache Maven Packages", organization.getLogin());
						final String url = String.format("https://maven.pkg.github.com/%1$s/%2$s", organization.getLogin(), repositoryName);
						profile.distributionManagement(DistributionRepository.builder().id(MAVEN_REPOSITORYID_DEPLOYMENT).name(name).url(url).build());
						profile.distributionManagement(DistributionSnapshotRepository.builder().id(MAVEN_REPOSITORYID_DEPLOYMENT).name(name).url(url).build());
					}

					// Repositories
					final Policies policies = Repository.Policies.builder().enabled(true).build();
					profile.repository(Repository.builder().id(getMavenRepsitoryIDOrganization(organization.getLogin())).url(String.format("https://maven.pkg.github.com/%1$s/*", organization.getLogin())).releases(policies).snapshots(policies).build());

					pom.profile(profile.build());
				}

				projectPOM = pom.build_();
				POM.getXmlMapper().writeValue(projectDirectory.resolve(HProject.POM).toFile(), projectPOM);
			}

			{ // Create the root pom
				final Path relativePath = directory.relativize(projectDirectory);

				final POM.POMBuilder pom = POM.builder();
				pom.modelVersion("4.0.0");
				pom.artifactId(repositoryName).packaging("pom");
				pom.parent(Parent.builder().groupId(projectPOM.getGroupId()).artifactId(projectPOM.getArtifactId()).version(projectPOM.getVersion()).relativePath(PathSpec.UNIX.canonizePath(relativePath.resolve(HProject.POM).toString())).build());
				pom.name(create.getName()).description(create.getDescription());
				pom.module(PathSpec.UNIX.canonizePath(relativePath.toString()));
				POM.getXmlMapper().writeValue(directory.resolve(HProject.POM).toFile(), pom.build_());
			}
			commit(git, getIssue() + " Create pom files", HProject.POM, projectName + "/" + HProject.POM);
		}

		{ // Modify the root project pom.xml file
			log.info("Modifying root POM & ignoring the repository");
			if (!HGit.isBranch(rootGit, branch)) rootGit.checkout().setCreateBranch(true).setName(branch).call();

			try {
				updateMultiModulePOM(rootDirectory.resolve(HProject.POM), create.getProtection(), rootDirectory.relativize(directory).toString());
			} catch (XPathExpressionException | SAXException | ParserConfigurationException | TransformerFactoryConfigurationError | TransformerException e) {
				throw new RuntimeException(e);
			}

			final LinkedHashSet<String> ignores = new LinkedHashSet<>(GitIgnore.load(rootDirectory).getLines());
			ignores.add("/" + repositoryName);
			new GitIgnore(new ArrayList<>(ignores)).store(rootDirectory);

			commit(rootGit, getIssue() + " Added " + create.getName(), HProject.POM, Constants.GITIGNORE_FILENAME);
		}

		{ // Set up github actions
			final String message = "Setting up github action for Java CI with Maven";
			log.info(message);

			final Set<String> dependencies = new LinkedHashSet<>();
			if (create.getParent() != null) dependencies.add(create.getParent());
			dependencies.addAll(create.getDependencies());

			final boolean protectionPublic = MavenProject.Protection.Public.equals(create.getProtection());
			final String settingsRelative = ".github/workflows/maven-settings.xml";
			{
				final Path settingsPath = directory.resolve(settingsRelative);
				Files.createDirectories(settingsPath.getParent());
				final Settings.SettingsBuilder settings = Settings.builder();
				settings.server(Server.builder().id(MAVEN_REPOSITORYID_DEPLOYMENT).username("${env." + GITHUB_ACTOR + "}").password("${env." + GITHUB_TOKEN + "}").build());
				settings.server(Server.builder().id(getMavenRepsitoryIDOrganization(organization.getLogin())).username("${env." + (protectionPublic ? GITHUB_ACTOR : PACKAGESREAD_USERNAME) + "}").password("${env." + (protectionPublic ? GITHUB_TOKEN : PACKAGESREAD_TOKEN) + "}").build());
				POM.getXmlMapper().writeValue(settingsPath.toFile(), settings.build());
			}

			final String workflowRelative = ".github/workflows/maven.yml";
			{
				final Path workflowPath = directory.resolve(workflowRelative);
				Files.createDirectories(workflowPath.getParent());
				HGHActions.getMapper().writeValue(workflowPath.toFile(), HGHActions.createMavenWorkflow(repositoryName, repository.getDefaultBranch(), "${GITHUB_WORKSPACE}/.github/workflows/maven-settings.xml", Collections.emptySet(), protectionPublic ? Collections.emptySet() : HCollection.asSet(PACKAGESREAD_USERNAME, PACKAGESREAD_TOKEN)));
			}
			commit(git, getIssue() + " " + message, settingsRelative, workflowRelative);
		}

		// Note that we do not push the branch or open PRs, there is another command for that
		log.info("Success!");
		return IStandardCommand.SUCCESS;
	}
}
