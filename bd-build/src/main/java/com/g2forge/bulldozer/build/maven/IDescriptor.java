package com.g2forge.bulldozer.build.maven;

public interface IDescriptor {
	public String getArtifactId();

	public String getGroupId();

	public String getPackaging();

	public String getScope();

	public String getVersion();
}