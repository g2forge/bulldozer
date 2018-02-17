package com.g2forge.bulldozer.build.model.maven;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.g2forge.alexandria.java.enums.HEnum;
import com.g2forge.bulldozer.build.maven.POM;
import com.g2forge.bulldozer.build.maven.Profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@AllArgsConstructor
public class MavenProjects {
	protected static final XmlMapper mapper = new XmlMapper();

	protected final Path rootPOM;

	@Getter(lazy = true)
	private final List<MavenProject> projects = computeProjects();

	protected List<MavenProject> computeProjects() {
		try {
			final POM pom = mapper.readValue(getRootPOM().toFile(), POM.class);
			final List<MavenProject> retVal = new ArrayList<>();
			pom.getModules().stream().map(relative -> new MavenProject(Paths.get(relative), MavenProject.Protection.Public)).forEach(retVal::add);
			for (Profile profile : pom.getProfiles()) {
				final MavenProject.Protection protection = HEnum.valueOfInsensitive(MavenProject.Protection.class, profile.getId());
				if (protection == null) continue;
				profile.getModules().stream().map(relative -> new MavenProject(Paths.get(relative), protection)).forEach(retVal::add);
			}
			retVal.add(new MavenProject(Paths.get("."), retVal.stream().map(MavenProject::getProtection).max(Comparator.naturalOrder()).get()));
			return retVal;
		} catch (Throwable throwable) {
			throw new RuntimeException(throwable);
		}
	}
}