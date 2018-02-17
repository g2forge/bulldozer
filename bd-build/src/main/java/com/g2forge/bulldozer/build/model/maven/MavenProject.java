package com.g2forge.bulldozer.build.model.maven;

import java.nio.file.Path;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class MavenProject {
	public enum Protection {
		Public,
		Private,
		Sandbox
	}

	protected final Path relative;

	protected final MavenProject.Protection protection;
}