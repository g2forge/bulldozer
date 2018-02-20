package com.g2forge.bulldozer.build.json;

import java.io.IOException;

import org.semver.Version;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class VersionDeserializer extends JsonDeserializer<Version> {
	@Override
	public Version deserialize(JsonParser parser, DeserializationContext context) throws IOException, JsonProcessingException {
		final ObjectCodec codec = parser.getCodec();
		final String string = codec.readValue(parser, String.class);
		return Version.parse(string);
	}
}