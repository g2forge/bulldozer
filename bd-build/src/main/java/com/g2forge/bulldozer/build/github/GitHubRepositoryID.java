package com.g2forge.bulldozer.build.github;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class GitHubRepositoryID {
	protected static final Pattern GITHUB_URL_SSH = Pattern.compile("git@github\\.com:([^/]+)/([^.]+)\\.git");

	public static GitHubRepositoryID fromURL(String url) {
		final Matcher matcher = GITHUB_URL_SSH.matcher(url);
		if (!matcher.matches()) throw new IllegalArgumentException();
		return new GitHubRepositoryID(matcher.group(1), matcher.group(2));
	}

	protected final String organization;

	protected final String repository;

	public String toGitHubName() {
		return String.format("%1$s/%2$s", getOrganization(), getRepository());
	}

	public String toGitSSHURL() {
		return String.format("git@github.com:%1$s/%2$s.git", getOrganization(), getRepository());
	}

	public String toWebsiteURL() {
		return String.format("https://github.com/%1$s/%2$s", getOrganization(), getRepository());
	}
}
