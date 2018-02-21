package com.g2forge.bulldozer.build.maven.build.plugin;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.alexandria.service.BasicServiceLoader;

import lombok.AccessLevel;
import lombok.Getter;

public class PluginDeserializer extends StdDeserializer<IPlugin> {
	private static final long serialVersionUID = 6861584786526442189L;

	@Getter(lazy = true, value = AccessLevel.PROTECTED)
	private static final Map<String, ? extends Class<? extends IPlugin>> plugins = new BasicServiceLoader<IPlugin>(IPlugin.class).find().stream().collect(Collectors.toMap(p -> {
		final Plugin annotation = p.getAnnotation(Plugin.class);
		return annotation.groupId() + ":" + annotation.artifactId();
	}, IFunction1.identity()));

	protected PluginDeserializer() {
		super(IPlugin.class);
	}

	@Override
	public IPlugin deserialize(JsonParser parser, DeserializationContext context) throws IOException, JsonProcessingException {
		final ObjectMapper mapper = (ObjectMapper) parser.getCodec();
		final ObjectNode tree = (ObjectNode) mapper.readTree(parser);

		final Class<? extends IPlugin> type = getPlugins().get(tree.get("groupId").asText() + ":" + tree.get("artifactId").asText());
		if (type != null) return mapper.treeToValue(tree, type);
		return mapper.treeToValue(tree, GenericPlugin.class);
	}

}