package com.g2forge.bulldozer.build.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Singular;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@RequiredArgsConstructor
public class BulldozerTemp {
	public static final String BULLDOZER_TEMP = "bulldozer-temp.json";

	protected String commit;

	@Singular
	protected List<String> otherCommits;

	protected String group;

	protected String version;

	protected BulldozerDependencies dependencies;

	protected String parentGroup;

	public boolean isValidForCommit(String commit) {
		return getCommit().equals(commit) || ((getOtherCommits() != null) && getOtherCommits().contains(commit));
	}
}