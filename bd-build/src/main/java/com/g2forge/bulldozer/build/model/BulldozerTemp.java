package com.g2forge.bulldozer.build.model;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Singular;

@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class BulldozerTemp {
	public static final String BULLDOZER_TEMP = "bulldozer-temp.json";

	protected String hash;

	protected String group;

	protected String version;

	@Singular
	protected Map<String, String> dependencies;
}