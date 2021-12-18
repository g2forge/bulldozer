package com.g2forge.bulldozer.build.maven.distribution;

import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@XmlRootElement(name = "snapshotRepository")
public class DistributionSnapshotRepository implements IDistributionRepository {
	protected final String id;

	protected final String name;

	protected final String url;
}