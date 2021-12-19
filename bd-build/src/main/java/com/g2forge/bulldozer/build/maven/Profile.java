package com.g2forge.bulldozer.build.maven;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.g2forge.bulldozer.build.maven.distribution.DistributionRepository;
import com.g2forge.bulldozer.build.maven.distribution.DistributionSnapshotRepository;
import com.g2forge.bulldozer.build.maven.distribution.IDistributionRepository;

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

	@JacksonXmlElementWrapper(useWrapping = false)
	@XmlElements({ @XmlElement(name = "repository", type = DistributionRepository.class), @XmlElement(name = "snapshotRepository", type = DistributionSnapshotRepository.class) })
	@Singular("distributionManagement")
	protected final List<IDistributionRepository> distributionManagement;
}