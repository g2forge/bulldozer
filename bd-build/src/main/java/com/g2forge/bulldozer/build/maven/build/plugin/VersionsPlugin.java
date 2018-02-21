package com.g2forge.bulldozer.build.maven.build.plugin;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.g2forge.alexandria.annotations.service.Service;
import com.g2forge.bulldozer.build.maven.Descriptor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
@JsonDeserialize(using = JsonDeserializer.None.class)
@Plugin(groupId = "org.codehaus.mojo", artifactId = "versions-maven-plugin")
@Service(IPlugin.class)
@JsonPropertyOrder({ "groupId", "artifactId", "version", "configuration" })
public class VersionsPlugin implements IPlugin {
	@Data
	@Builder
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(Include.NON_EMPTY)
	public static class Configuration {
		@Singular
		protected final List<Property> properties;
	}

	@Data
	@Builder
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(Include.NON_EMPTY)
	public static class Property {
		protected final String name;

		@JacksonXmlElementWrapper(localName = "dependencies")
		@JacksonXmlProperty(localName = "dependency")
		@Singular
		protected final List<Descriptor> dependencies;
	}

	protected final String version;

	protected final Configuration configuration;

	@Override
	public String getArtifactId() {
		return getClass().getAnnotation(Plugin.class).artifactId();
	}

	@Override
	public String getGroupId() {
		return getClass().getAnnotation(Plugin.class).groupId();
	}
}
