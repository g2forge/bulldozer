package com.g2forge.bulldozer.build.maven.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@JsonInclude(Include.NON_EMPTY)
public class Developer {
	protected final String name;

	protected final String email;

	protected final String organization;

	protected final String organizationUrl;
}