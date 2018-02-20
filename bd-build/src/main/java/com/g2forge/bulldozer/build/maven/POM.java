package com.g2forge.bulldozer.build.maven;

import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.g2forge.bulldozer.build.maven.build.Build;
import com.g2forge.bulldozer.build.maven.metadata.Developer;
import com.g2forge.bulldozer.build.maven.metadata.License;
import com.g2forge.bulldozer.build.maven.metadata.SCM;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder(buildMethodName = "build_")
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
@JacksonXmlRootElement(localName = "project")
public class POM implements IDescriptor {
	protected final String modelVersion;

	protected final String groupId;

	protected final String artifactId;

	protected final String packaging;

	protected final String version;

	protected final String scope;

	protected final Parent parent;

	@Singular
	protected final Map<String, String> properties;

	protected final String name;

	protected final String description;

	protected final String url;

	@XmlElementWrapper
	@XmlElement(name = "developer")
	@Singular
	protected final List<Developer> developers;

	@XmlElementWrapper
	@XmlElement(name = "license")
	@Singular
	protected final List<License> licenses;

	protected final SCM scm;

	@XmlElementWrapper
	@XmlElement(name = "module")
	@Singular
	protected final List<String> modules;

	@XmlElementWrapper
	@XmlElement(name = "profile")
	@Singular
	protected final List<Profile> profiles;

	protected final Build build;
}