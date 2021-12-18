package com.g2forge.bulldozer.build.maven;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DistributionRepository {
	protected final String id;
	
	protected final String name;

	protected final String url;
}