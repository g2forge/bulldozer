package com.g2forge.bulldozer.build.maven;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Repository {
	@Data
	@Builder
	@AllArgsConstructor
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Policies {
		public static enum UpdatePolicy {
			ALWAYS,
			DAILY,
			NEVER
		}

		protected final Boolean enabled;

		protected final UpdatePolicy updatePolicy;
	}

	protected final String id;

	protected final String url;

	protected final Policies releases;

	protected final Policies snapshots;
}