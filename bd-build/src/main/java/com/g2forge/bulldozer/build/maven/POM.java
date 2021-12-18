package com.g2forge.bulldozer.build.maven;

import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import com.g2forge.bulldozer.build.maven.build.Build;
import com.g2forge.bulldozer.build.maven.metadata.Developer;
import com.g2forge.bulldozer.build.maven.metadata.License;
import com.g2forge.bulldozer.build.maven.metadata.SCM;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Singular;

@Data
@Builder(buildMethodName = "build_")
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
@JacksonXmlRootElement(localName = "project")
public class POM implements IDescriptor {
	@Getter(lazy = true)
	private static final XmlMapper xmlMapper = createXMLMapper();

	protected static XmlMapper createXMLMapper() {
		final XmlMapper retVal = new XmlMapper();
		retVal.registerModule(new JaxbAnnotationModule());
		retVal.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);
		retVal.enable(SerializationFeature.INDENT_OUTPUT);
		return retVal;
	}

	@XmlAttribute
	protected final String xmlns = "http://maven.apache.org/POM/4.0.0";

	@XmlAttribute(name = "xmlns:xsi")
	protected final String xmlns_xsi = "http://www.w3.org/2001/XMLSchema-instance";

	@XmlAttribute(name = "xsi:schemaLocation")
	protected final String xsi_schemaLocation = "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd";

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
	@XmlElement(name = "pluginRepository")
	@Singular
	protected final List<Repository> pluginRepositories;

	@XmlElementWrapper
	@XmlElement(name = "repository")
	@Singular
	protected final List<Repository> repositories;

	protected final Build build;

	@XmlElementWrapper
	@XmlElement(name = "profile")
	@Singular
	protected final List<Profile> profiles;
}