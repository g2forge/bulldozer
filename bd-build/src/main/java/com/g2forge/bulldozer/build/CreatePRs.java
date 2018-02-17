package com.g2forge.bulldozer.build;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.RefSpec;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.semver.Version;
import org.slf4j.event.Level;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.g2forge.alexandria.data.graph.HGraph;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.alexandria.wizard.CommandLineStringInput;
import com.g2forge.alexandria.wizard.PropertyStringInput;
import com.g2forge.alexandria.wizard.UserPasswordInput;
import com.g2forge.alexandria.wizard.UserStringInput;
import com.g2forge.bulldozer.build.github.GitHubRepositoryID;
import com.g2forge.bulldozer.build.model.BulldozerProject;
import com.g2forge.bulldozer.build.model.Context;
import com.g2forge.enigma.document.Block;
import com.g2forge.enigma.document.Text;
import com.g2forge.enigma.document.convert.md.MDRenderer;
import com.g2forge.gearbox.git.GitConfig;
import com.g2forge.gearbox.git.HGit;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class CreatePRs {
	public static void main(String[] args) throws JsonParseException, JsonMappingException, IOException, GitAPIException {
		final String branch = new CommandLineStringInput(args, 1).fallback(new UserStringInput("Branch", true)).get();
		final String title = new CommandLineStringInput(args, 2).fallback(new UserStringInput("Title", true)).get();
		final CreatePRs createPRs = new CreatePRs(new Context<BulldozerProject>(BulldozerProject::new, Paths.get(args[0])), branch, title);
		createPRs.createPRs();
	}

	protected final Context<BulldozerProject> context;

	protected final String branch;

	protected final String title;

	public void createPRs() throws GitAPIException, IOException {
		HLog.getLogControl().setLogLevel(Level.INFO);
		// Fail if any repositories are dirty
		getContext().failIfDirty();

		// Connect to github
		final GitHub github;
		{
			final String user = new PropertyStringInput("github.user").fallback(new UserPasswordInput("GitHub Username")).get();
			final String token = new PropertyStringInput("github.token").fallback(new UserPasswordInput("GitHub OAuth Token")).get();
			github = new GitHubBuilder().withOAuthToken(token, user).build();
		}

		// Find the projects which have the relevant branch
		final List<String> projects = getContext().getProjects().values().stream().filter(p -> HGit.isBranch(p.getGit(), getBranch())).map(BulldozerProject::getName).collect(Collectors.toList());
		// Topologically sort the projects
		final List<String> order = HGraph.toposort(projects, p -> HCollection.intersection(getContext().getNameToProject().get(p).getDependencies().getTransitive().keySet(), projects), false);

		final TransportConfigCallback transportConfig;
		{
			final String key = new PropertyStringInput("ssh.key.file").fallback(new UserPasswordInput("SSH Key File")).get();
			final String passphrase = new PropertyStringInput("ssh.key.passphrase").fallback(new UserPasswordInput(String.format("SSH Passphrase for %1$s", key))).get();
			transportConfig = HGit.createTransportConfig(key, passphrase);
		}

		// In topological order...
		final Map<String, GHPullRequest> projectToPullRequest = new HashMap<>();
		for (String name : order) {
			final BulldozerProject project = getContext().getProjects().get(name);
			final String remote = HGit.getMyRemote(project.getGit());

			{ // Push the branch
				log.info(String.format("Pushing %1$s to remote %3$s:%2$s", getBranch(), remote, project.getName()));
				project.getGit().push().setTransportConfigCallback(transportConfig).setRemote(remote).setRefSpecs(new RefSpec(getBranch() + ":" + getBranch())).call();
				HGit.setTracking(project.getGit(), getBranch(), remote, null);
				log.info(String.format("Successfully pushed %1$s", project.getName()));
			}

			{ // Open the PR
				final GitHubRepositoryID fork = GitHubRepositoryID.fromURL(new GitConfig(project.getGit()).getRemote(remote).getURL());
				final String base = Constants.MASTER;
				final String head = String.format("%1$s:%2$s", fork.getOrganization(), getBranch());

				log.info(String.format("Opening pull request for %1$s", project.getName()));

				final GHRepository repository = github.getRepository(project.getGithubMaster().toGitHubName());
				final List<GHPullRequest> pullRequests = StreamSupport.stream(repository.queryPullRequests().head(head).base(base).list().spliterator(), false).collect(Collectors.toList());
				if (pullRequests.size() > 1) throw new IllegalStateException();
				else if (pullRequests.isEmpty()) {
					final String body;
					{ // Build the PR body
						final Block.BlockBuilder builder = Block.builder().type(Block.Type.Paragraph);
						final Set<String> dependencies = project.getDependencies().getImmediate().keySet();
						if (!dependencies.isEmpty()) {
							builder.content(new Text("Depends on "));
							final Text comma = new Text(", ");
							boolean first = true;
							for (String dependency : dependencies) {
								if (first) first = false;
								else builder.content(comma);
								builder.content(new Text(projectToPullRequest.get(dependency).getHtmlUrl().toString()));
							}
						}
						body = new MDRenderer().render(builder.build());
					}

					final GHPullRequest pr = repository.createPullRequest(getTitle(), head, base, body);
					projectToPullRequest.put(name, pr);
					log.info(String.format("Opened %1$s", pr.getHtmlUrl()));

					pr.setLabels(base);
					pr.addAssignees(github.getMyself());
					final List<GHMilestone> milestones = StreamSupport.stream(repository.listMilestones(GHIssueState.OPEN).spliterator(), false).collect(Collectors.toList());
					if (!milestones.isEmpty()) {
						final String projectVersion = Version.parse(project.getVersion()).toReleaseVersion().toString();
						final GHMilestone milestone;
						try {
							milestone = HCollection.getOne(milestones.stream().filter(m -> {
								final int space = m.getTitle().indexOf(' ');
								final String version = space >= 0 ? m.getTitle().substring(0, space) : m.getTitle();
								return version.equals(projectVersion);
							}).collect(Collectors.toList()));
						} catch (NoSuchElementException e) {
							throw new IllegalStateException(String.format("Could not find milestone %1$s for %2$s, options are: %3$2", projectVersion, pr.getHtmlUrl(), milestones), e);
						}
						repository.getIssue(pr.getNumber()).setMilestone(milestone);
					}
					log.info("Set labels, assignee & milestone");
				} else {
					final GHPullRequest pr = HCollection.getOne(pullRequests);
					log.info(String.format("Found %1$s", pr.getHtmlUrl()));
					projectToPullRequest.put(name, pr);
				}
			}
		}
	}
}
