package com.g2forge.bulldozer.build.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class BulldozerTemp {
	public static final String BULLDOZER_TEMP = "bulldozer-temp.json";

	protected String key;

	protected String group;

	protected String version;

	protected BulldozerDependencies dependencies;
}