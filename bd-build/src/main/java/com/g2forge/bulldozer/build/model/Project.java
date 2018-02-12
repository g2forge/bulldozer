package com.g2forge.bulldozer.build.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class Project {
	public enum Protection {
		Public,
		Private,
		Sandbox
	}

	protected final String name;

	protected final Project.Protection protection;
}