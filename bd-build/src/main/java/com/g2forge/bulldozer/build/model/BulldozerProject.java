package com.g2forge.bulldozer.build.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;

import com.g2forge.alexandria.java.adt.tuple.ITuple1G_;
import com.g2forge.alexandria.java.adt.tuple.ITuple2G_;
import com.g2forge.alexandria.java.adt.tuple.implementations.Tuple2G_I;
import com.g2forge.alexandria.java.close.ICloseable;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.function.IConsumer2;
import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.alexandria.java.function.ISupplier;
import com.g2forge.alexandria.java.io.HIO;
import com.g2forge.alexandria.java.io.RuntimeIOException;
import com.g2forge.bulldozer.build.github.GitHubRepositoryID;
import com.g2forge.bulldozer.build.maven.Descriptor;
import com.g2forge.bulldozer.build.maven.IMaven;
import com.g2forge.bulldozer.build.maven.POM;
import com.g2forge.bulldozer.build.model.maven.MavenProject;
import com.g2forge.gearbox.git.GitConfig;
import com.g2forge.gearbox.git.HGit;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Getter
@RequiredArgsConstructor
@Slf4j
public class BulldozerProject implements ICloseable {
	protected static final String BRANCH_DUMMY = "bulldozer-dummy";

	protected final Context<? extends BulldozerProject> context;

	protected final MavenProject project;

	@Getter(lazy = true)
	private final String group = loadTemp(BulldozerTemp::getGroup, BulldozerTemp::setGroup, () -> getContext().getMaven().evaluate(getDirectory(), "project.groupId"));

	@Getter(lazy = true)
	private final String version = loadTemp(BulldozerTemp::getVersion, BulldozerTemp::setVersion, () -> getContext().getMaven().evaluate(getDirectory(), "project.version"));

	@Getter(lazy = true)
	private final Path directory = computeDirectory();

	@Getter(lazy = true)
	private final Git git = computeGit();

	@Getter(lazy = true)
	private final POM pom = computePOM();

	@Getter(lazy = true)
	private final BulldozerDependencies dependencies = computeDependencies();

	@Getter(lazy = true)
	private final GitHubRepositoryID githubMaster = GitHubRepositoryID.fromURL(new GitConfig(getGit()).getOrigin().getURL());

	@Getter(lazy = true, value = AccessLevel.PROTECTED)
	private final List<AutoCloseable> closeables = new ArrayList<>();

	@Override
	public void close() {
		HIO.closeAll(getCloseables());
	}

	protected BulldozerDependencies computeDependencies() {
		final Map<String, ? extends BulldozerProject> nameToProject = getContext().getNameToProject();
		final Map<String, ? extends BulldozerProject> groupToProject = getContext().getGroupToProject();
		return loadTemp(BulldozerTemp::getDependencies, BulldozerTemp::setDependencies, () -> {
			final String name = getName();
			log.info("Loading dependencies for {}", name);
			// Run maven dependencies and filter the output down to usable information
			final Map<String, List<ITuple2G_<Descriptor, Boolean>>> grouped = getContext().getMaven().dependencyTree(getDirectory(), true, groupToProject.keySet().stream().map(g -> g + ":*").collect(Collectors.toList()))/*.map(new TapFunction<>(System.out::println))*/.filter(line -> {
				if (!line.startsWith("[INFO]")) return false;
				for (BulldozerProject publicProject : nameToProject.values()) {
					if (line.contains("- " + publicProject.getGroup())) return true;
				}
				return false;
			}).map(line -> new Tuple2G_I<>(Descriptor.fromString(line.substring(line.indexOf("- ") + 2)), line.startsWith("[INFO] \\- ") || line.startsWith("[INFO] +- "))).filter(t -> !t.get0().getGroupId().equals(nameToProject.get(name).getGroup())).collect(Collectors.groupingBy(t -> t.get0().getGroupId()));
			// Extract the per-project version and make sure we only ever depend on one version
			final BulldozerDependencies.BulldozerDependenciesBuilder builder = BulldozerDependencies.builder();
			for (List<ITuple2G_<Descriptor, Boolean>> tuples : grouped.values()) {
				// Aside from versions we only have to look at the first tuple, since they're all the same
				final String group = tuples.get(0).get0().getGroupId();

				final Set<String> versions = tuples.stream().map(ITuple1G_::get0).map(Descriptor::getVersion).collect(Collectors.toSet());
				if (versions.size() > 1) throw new IllegalArgumentException(String.format("%3$s depends on multiple versions of the project \"%1$s\": %2$s", group, versions, name));
				final String version = HCollection.getOne(versions);
				final String dependency = groupToProject.get(group).getName();

				// If any of the dependencies are immediate, then the project dependency is
				final boolean immediate = tuples.stream().filter(ITuple2G_::get1).findAny().isPresent();
				if (immediate) builder.immediate(dependency, version);
				builder.transitive(dependency, version);
			}
			final BulldozerDependencies retVal = builder.build();
			log.info("Found dependencies for {}: {}", name, retVal.getTransitive().keySet());
			return retVal;
		});
	}

	protected Path computeDirectory() {
		return getContext().getRoot().resolve(getProject().getRelative());
	}

	protected Git computeGit() {
		final Git retVal = HGit.createGit(getContext().getRoot().resolve(getProject().getRelative().getName(0)), false);
		getCloseables().add(retVal);
		return retVal;
	}

	protected POM computePOM() {
		try {
			return getContext().getXmlMapper().readValue(getDirectory().resolve(IMaven.POM_XML).toFile(), POM.class);
		} catch (IOException e) {
			throw new RuntimeIOException(String.format("Failed to read %1$s for %2$s!", IMaven.POM_XML, getProject().getRelative()), e);
		}
	}

	public String getArtifactId() {
		return getPom().getArtifactId();
	}

	public String getName() {
		return getArtifactId();
	}

	public <T> T loadTemp(IFunction1<BulldozerTemp, T> getter, IConsumer2<BulldozerTemp, T> setter, ISupplier<T> generator) {
		final String commit;
		try {
			commit = getGit().getRepository().findRef(Constants.HEAD).getObjectId().getName();
		} catch (IOException exception) {
			throw new RuntimeIOException(String.format("Failed to determine commit for %1$s!", getName()), exception);
		}

		final Path path = getDirectory().resolve(BulldozerTemp.BULLDOZER_TEMP);
		BulldozerTemp temp = null;
		if (Files.exists(path)) {
			try {
				final BulldozerTemp read = getContext().getObjectMapper().readValue(path.toFile(), BulldozerTemp.class);
				if (read.getKey().equals(commit)) temp = read;
				else Files.delete(path);
			} catch (IOException exception) {
				log.warn(String.format("Failed to read bulldozer temp data for %1$s, will regenerate...", getName()));
			}
		}
		if (temp == null) {
			temp = new BulldozerTemp();
			temp.setKey(commit);
		}
		final T retVal0 = getter.apply(temp);
		if (retVal0 != null) return retVal0;

		final T retVal1;
		try {
			retVal1 = generator.get();
		} catch (Throwable throwable) {
			throw new RuntimeException(String.format("Failed to generate temp data for %1$s!", getName()), throwable);
		}
		setter.accept(temp, retVal1);
		try {
			getContext().getObjectMapper().writeValue(path.toFile(), temp);
		} catch (IOException exception) {
			throw new RuntimeIOException(String.format("Failed to update bulldozer temp data for %1$s!", getName()), exception);
		}
		return retVal1;
	}
}