package com.g2forge.bulldozer.build.json;

import java.io.IOException;

import org.semver.Version;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class VersionSerializer extends JsonSerializer<Version> {
	@Override
	public void serialize(Version version, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
		jsonGenerator.writeObject(version.toString());
	}
}