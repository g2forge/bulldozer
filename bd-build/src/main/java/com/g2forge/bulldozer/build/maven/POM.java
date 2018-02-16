package com.g2forge.bulldozer.build.maven;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class POM {
	protected final String artifactId;

	protected final String groupId;

	protected final String version;

	protected final String name;

	protected final String description;

	@Singular
	protected final List<String> modules;

	@Singular
	protected final List<Profile> profiles;
}