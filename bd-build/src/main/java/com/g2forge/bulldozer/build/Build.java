package com.g2forge.bulldozer.build;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.g2forge.alexandria.java.enums.HEnum;
import com.g2forge.bulldozer.build.Build.Project.Protection;
import com.g2forge.bulldozer.build.maven.POM;
import com.g2forge.bulldozer.build.maven.Profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

public class Build {
	@Data
	@Builder
	@AllArgsConstructor
	public static class Project {
		public enum Protection {
			Public,
			Private,
			Sandbox
		}

		protected final String name;

		protected final Protection protection;
	}

	@Data
	@Builder
	@AllArgsConstructor
	public static class Projects {
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
					final Protection protection = HEnum.valueOfInsensitive(Project.Protection.class, profile.getId());
					if (protection == null) continue;
					profile.getModules().stream().map(name -> new Project(name, protection)).forEach(retVal::add);
				}
				return retVal;
			} catch (Throwable throwable) {
				throw new RuntimeException(throwable);
			}
		}
	}

	public static void main(String[] args) throws JsonParseException, JsonMappingException, IOException {
		final Projects projects = new Projects(Paths.get(args[0]));
		projects.getProjects().forEach(System.out::println);
	}
}
