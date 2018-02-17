package com.g2forge.bulldozer.build.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;

import com.g2forge.alexandria.java.close.ICloseable;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.function.IConsumer2;
import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.alexandria.java.function.ISupplier;
import com.g2forge.alexandria.java.io.HIO;
import com.g2forge.alexandria.java.io.RuntimeIOException;
import com.g2forge.bulldozer.build.maven.Descriptor;
import com.g2forge.bulldozer.build.maven.IMaven;
import com.g2forge.bulldozer.build.maven.POM;
import com.g2forge.bulldozer.build.model.maven.MavenProject;
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
	private final Map<String, String> dependencies = computeDependencies();

	@Getter(lazy = true, value = AccessLevel.PROTECTED)
	private final List<AutoCloseable> closeables = new ArrayList<>();

	@Override
	public void close() {
		HIO.closeAll(getCloseables());
	}

	protected Map<String, String> computeDependencies() {
		final Map<String, ? extends BulldozerProject> nameToProject = getContext().getNameToProject();
		final Map<String, ? extends BulldozerProject> groupToProject = getContext().getGroupToProject();
		return loadTemp(BulldozerTemp::getDependencies, BulldozerTemp::setDependencies, () -> {
			final String name = getName();
			log.info("Loading dependencies for {}", name);
			// Run maven dependencies and filter the output down to usable information
			final Map<String, List<Descriptor>> grouped = getContext().getMaven().dependencyTree(getContext().getRoot().resolve(name), true, groupToProject.keySet().stream().map(g -> g + ":*").collect(Collectors.toList()))/*.map(new TapFunction<>(System.out::println))*/.filter(line -> {
				if (!line.startsWith("[INFO]")) return false;
				for (BulldozerProject publicProject : nameToProject.values()) {
					if (line.contains("- " + publicProject.getGroup())) return true;
				}
				return false;
			}).map(line -> Descriptor.fromString(line.substring(line.indexOf("- ") + 2))).filter(descriptor -> !descriptor.getGroup().equals(nameToProject.get(name).getGroup())).collect(Collectors.groupingBy(Descriptor::getGroup));
			// Extract the per-project version and make sure we only ever depend on one version
			final Map<String, String> retVal = new LinkedHashMap<>();
			for (List<Descriptor> descriptors : grouped.values()) {
				final Set<String> groupVersions = descriptors.stream().map(Descriptor::getVersion).collect(Collectors.toSet());
				if (groupVersions.size() > 1) throw new IllegalArgumentException(String.format("%3$s depends on multiple versions of the project \"%1$s\": %2$s", descriptors.get(0).getGroup(), groupVersions, name));
				final String group = descriptors.get(0).getGroup();
				retVal.put(groupToProject.get(group).getName(), HCollection.getOne(groupVersions));
			}
			log.info("Found dependencies for {}: {}", name, retVal);
			return retVal;
		});
	}

	protected Path computeDirectory() {
		return getContext().getRoot().resolve(getName());
	}

	protected Git computeGit() {
		final String name = getName();
		final int index = name.indexOf('/');
		final Git retVal = HGit.createGit(getContext().getRoot().resolve(index > 0 ? name.substring(0, index) : name), false);
		getCloseables().add(retVal);
		return retVal;
	}

	protected POM computePOM() {
		try {
			return getContext().getXmlMapper().readValue(getDirectory().resolve(IMaven.POM_XML).toFile(), POM.class);
		} catch (IOException e) {
			throw new RuntimeIOException(String.format("Failed to read %1$s for %2$s!", IMaven.POM_XML, getName()), e);
		}
	}

	public String getName() {
		return getProject().getName();
	}

	public <T> T loadTemp(IFunction1<BulldozerTemp, T> getter, IConsumer2<BulldozerTemp, T> setter, ISupplier<T> generator) {
		final String commit;
		try {
			commit = getGit().getRepository().findRef(Constants.HEAD).getObjectId().getName();
		} catch (IOException e) {
			throw new RuntimeIOException(e);
		}

		final Path path = getDirectory().resolve(BulldozerTemp.BULLDOZER_TEMP);
		BulldozerTemp temp = null;
		if (Files.exists(path)) {
			try {
				final BulldozerTemp read = getContext().getObjectMapper().readValue(path.toFile(), BulldozerTemp.class);
				if (read.getHash().equals(commit)) temp = read;
				else Files.delete(path);
			} catch (IOException e) {
				throw new RuntimeIOException(e);
			}
		}
		if (temp == null) {
			temp = new BulldozerTemp();
			temp.setHash(commit);
		}
		final T retVal0 = getter.apply(temp);
		if (retVal0 != null) return retVal0;

		final T retVal1 = generator.get();
		setter.accept(temp, retVal1);
		try {
			getContext().getObjectMapper().writeValue(path.toFile(), temp);
		} catch (IOException e) {
			throw new RuntimeIOException(e);
		}
		return retVal1;
	}
}