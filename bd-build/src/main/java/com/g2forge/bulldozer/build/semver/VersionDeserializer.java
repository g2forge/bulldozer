package com.g2forge.bulldozer.build.semver;

import java.io.IOException;

import org.semver.Version;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;

public class VersionDeserializer extends FromStringDeserializer<Version> {
	private static final long serialVersionUID = 3051976919716658423L;

	protected VersionDeserializer() {
		super(Version.class);
	}

	@Override
	protected Version _deserialize(String value, DeserializationContext ctxt) throws IOException {
		return Version.parse(value);
	}
}