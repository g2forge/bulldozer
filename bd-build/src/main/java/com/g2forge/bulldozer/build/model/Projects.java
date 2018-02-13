package com.g2forge.bulldozer.build.model;

import java.nio.file.Path;
import java.util.ArrayList;
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
public class Projects {
	protected final Path rootPOM;

	@Getter(lazy = true)
	private final List<Project> projects = computeProjects();

	protected List<Project> computeProjects() {
		try {
			final XmlMapper mapper = new XmlMapper();
			final POM pom = mapper.readValue(getRootPOM().toFile(), POM.class);
			final List<Project> retVal = new ArrayList<>();
			pom.getModules().stream().map(name -> new Project(name, Project.Protection.Public)).forEach(retVal::add);
			for (Profile profile : pom.getProfiles()) {
				final Project.Protection protection = HEnum.valueOfInsensitive(Project.Protection.class, profile.getId());
				if (protection == null) continue;
				profile.getModules().stream().map(name -> new Project(name, protection)).forEach(retVal::add);
			}
			return retVal;
		} catch (Throwable throwable) {
			throw new RuntimeException(throwable);
		}
	}
}