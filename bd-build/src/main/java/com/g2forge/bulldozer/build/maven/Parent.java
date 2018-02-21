package com.g2forge.bulldozer.build.maven;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@JsonInclude(Include.NON_EMPTY)
public class Parent implements IDescriptor {
	protected final String groupId;

	protected final String artifactId;

	protected final String packaging;

	protected final String version;

	protected final String relativePath;

	@Override
	public String getScope() {
		return null;
	}
}