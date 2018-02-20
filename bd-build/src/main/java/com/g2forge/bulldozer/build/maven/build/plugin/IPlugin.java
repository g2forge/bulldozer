package com.g2forge.bulldozer.build.maven.build.plugin;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.g2forge.bulldozer.build.maven.IDescriptor;

@JsonDeserialize(using = PluginDeserializer.class)
public interface IPlugin extends IDescriptor {
	@Override
	public default String getPackaging() {
		return null;
	}

	@Override
	public default String getScope() {
		return null;
	}
}
