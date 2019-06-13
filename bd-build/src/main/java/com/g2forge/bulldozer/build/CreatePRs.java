package com.g2forge.bulldozer.build;

import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.RefSpec;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.semver.Version;
import org.slf4j.event.Level;

import com.g2forge.alexandria.adt.graph.HGraph;
import com.g2forge.alexandria.command.IConstructorCommand;
import com.g2forge.alexandria.command.IStandardCommand;
import com.g2forge.alexandria.command.exit.IExit;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.fluent.optional.NullableOptional;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.alexandria.wizard.CommandLineStringInput;
import com.g2forge.alexandria.wizard.PropertyStringInput;
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
public class CreatePRs implements IConstructorCommand {
	public static final IStandardCommand COMMAND_FACTORY = IStandardCommand.of(invocation -> {
		final String branch = new CommandLineStringInput(invocation, 1).fallback(new UserStringInput("Branch", true)).get();
		final String title = new CommandLineStringInput(invocation, 2).fallback(new UserStringInput("Title", true)).get();
		return new CreatePRs(new Context<BulldozerProject>(BulldozerProject::new, Paths.get(invocation.getArguments().get(0))), branch, title);
	});

	public static void main(String[] args) throws Throwable {
		IStandardCommand.main(args, COMMAND_FACTORY);
	}

	protected final Context<BulldozerProject> context;

	protected final String branch;

	protected final String title;
	
	protected final boolean allowDirty = new PropertyStringInput("bulldozer.allowdirty").map(Boolean::valueOf).fallback(NullableOptional.of(false)).get();

	@Override
	public IExit invoke() throws Throwable {
		HLog.getLogControl().setLogLevel(Level.INFO);
		// Fail if any repositories are dirty
		if (!allowDirty) getContext().failIfDirty();

		// Find the projects which have the relevant branch
		final List<String> projects = getContext().getProjects().values().stream().filter(p -> HGit.isBranch(p.getGit(), getBranch())).map(BulldozerProject::getName).collect(Collectors.toList());
		// Topologically sort the projects
		final List<String> order = HGraph.toposort(projects, p -> HCollection.intersection(getContext().getNameToProject().get(p).getDependencies().getTransitive().keySet(), projects), false);

		// In topological order...
		final Map<String, GHPullRequest> projectToPullRequest = new HashMap<>();
		for (String name : order) {
			final BulldozerProject project = getContext().getProjects().get(name);
			final String remote = HGit.getMyRemote(project.getGit());

			{ // Push the branch
				log.info(String.format("Pushing %1$s to remote %3$s:%2$s", getBranch(), remote, project.getName()));
				project.getGit().push().setTransportConfigCallback(getContext().getTransportConfig()).setRemote(remote).setRefSpecs(new RefSpec(getBranch() + ":" + getBranch())).call();
				new GitConfig(project.getGit()).getBranch(getBranch()).setTracking(remote, null).save();
				log.info(String.format("Successfully pushed %1$s", project.getName()));
			}

			{ // Open the PR
				final GitHubRepositoryID fork = GitHubRepositoryID.fromURL(new GitConfig(project.getGit()).getRemote(remote).getURL());
				final String base = Constants.MASTER;
				final String head = String.format("%1$s:%2$s", fork.getOrganization(), getBranch());

				log.info(String.format("Opening pull request for %1$s", project.getName()));

				final GHRepository repository = getContext().getGithub().getRepository(project.getGithubMaster().toGitHubName());
				final List<GHPullRequest> pullRequests = StreamSupport.stream(repository.queryPullRequests().head(head).base(base).list().spliterator(), false).collect(Collectors.toList());
				final GHPullRequest pr;
				if (pullRequests.size() > 1) throw new IllegalStateException(String.format("Found multiple pull requests: %1$s", pullRequests.stream().map(GHPullRequest::getHtmlUrl).map(URL::toString).collect(Collectors.joining(", "))));
				else if (pullRequests.isEmpty()) {
					final String body;
					{ // Build the PR body
						final Block.BlockBuilder builder = Block.builder().type(Block.Type.Paragraph);
						final Set<String> dependencies = project.getDependencies().getImmediate().keySet();
						if (!dependencies.isEmpty()) {
							final Text comma = new Text(", ");
							boolean first = true;
							for (String dependency : dependencies) {
								final GHPullRequest dependentPR = projectToPullRequest.get(dependency);
								if (dependentPR == null) continue;

								builder.content(first ? new Text("Depends on ") : comma);
								if (first) first = false;
								builder.content(new Text(dependentPR.getHtmlUrl().toString()));
							}
						}
						body = new MDRenderer().render(builder.build());
					}

					pr = repository.createPullRequest(getTitle(), head, base, body);
					log.info(String.format("Opened %1$s", pr.getHtmlUrl()));
				} else {
					pr = HCollection.getOne(pullRequests);
					log.info(String.format("Found %1$s", pr.getHtmlUrl()));
				}
				projectToPullRequest.put(name, pr);

				boolean changed = false;
				if (pr.getLabels().isEmpty()) {
					pr.setLabels(base);
					changed = true;
				}
				if (!pr.getAssignees().contains(getContext().getGithub().getMyself())) {
					pr.addAssignees(getContext().getGithub().getMyself());
					changed = true;
				}
				if (pr.getMilestone() == null) {
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
							throw new IllegalStateException(String.format("Could not find milestone %1$s for %2$s, options are: %3$s", projectVersion, pr.getHtmlUrl(), milestones.stream().map(GHMilestone::getTitle).collect(Collectors.joining(", "))), e);
						}
						repository.getIssue(pr.getNumber()).setMilestone(milestone);
						changed = true;
					}
				}
				if (changed) log.info("Set labels, assignee & milestone");
			}
		}
		return SUCCESS;
	}
}
