package com.g2forge.bulldozer.build.maven;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
@JacksonXmlRootElement(localName = "project")
public class Profile {
	protected final String id;

	@XmlElementWrapper
	@XmlElement(name = "module")
	@Singular
	protected final List<String> modules;
	
	@XmlElementWrapper
	@XmlElement(name = "pluginRepository")
	@Singular
	protected final List<Repository> pluginRepositories;

	@XmlElementWrapper
	@XmlElement(name = "repository")
	@Singular
	protected final List<Repository> repositories;
}