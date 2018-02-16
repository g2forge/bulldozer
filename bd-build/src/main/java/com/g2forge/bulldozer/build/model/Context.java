package com.g2forge.bulldozer.build.model;

import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.g2forge.bulldozer.build.maven.IMaven;
import com.g2forge.gearbox.functional.proxy.Proxifier;
import com.g2forge.gearbox.functional.runner.ProcessBuilderRunner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@AllArgsConstructor
public class Context {
	@Getter(lazy = true)
	private final XmlMapper xmlMapper = new XmlMapper();

	@Getter(lazy = true)
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Getter(lazy = true)
	private final IMaven maven = new Proxifier().generate(new ProcessBuilderRunner(), IMaven.class);

	protected final Path root;
}
