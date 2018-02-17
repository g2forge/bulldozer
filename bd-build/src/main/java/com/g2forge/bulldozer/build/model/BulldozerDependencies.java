package com.g2forge.bulldozer.build.model;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
@AllArgsConstructor
public class BulldozerDependencies {
	@Singular("transitive")
	protected final Map<String, String> transitive;

	@Singular("immediate")
	protected final Map<String, String> immediate;
}
