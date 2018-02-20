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
public class SCM {
	protected final String connection;

	protected final String developerConnection;

	protected final String url;

	protected final String tag;
}
