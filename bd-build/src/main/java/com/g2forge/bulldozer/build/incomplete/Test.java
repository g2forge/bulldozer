package com.g2forge.bulldozer.build.incomplete;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.g2forge.gearbox.functional.proxy.Proxifier;
import com.g2forge.gearbox.functional.runner.ProcessBuilderRunner;

public class Test {
	public static void main(String[] args) throws JsonParseException, JsonMappingException, IOException {
		final Path path = Paths.get(args[0]).resolve("enigma");
		final IMaven maven = new Proxifier().generate(new ProcessBuilderRunner("cmd", "/C"), IMaven.class);
		System.out.println(maven.mvn(path, "dependency:tree", "-Dverbose", "-Dincludes=com.g2forge.*:*"));
		
		// Do something to figure out what OS we're on the generate the shell prefix (bash vs cmd)
		// Do something smarter and more type safe for running maven to get dependency trees (includes is a csv list, and the only argument, verbose should be a flag, the goal should be hard-coded)
		// Read and parse the lines as they come in
		// Make sure the maven command is successful
		// Extract all other projects we found references to, and their versions (which better be only one version per project!)
		// Only look at lines the start with "[INFO]" and contain the below prefix (exact match), THEN regex to extract version, starting from index of exact match for group (a little tricky with the option paren for omitted dependencies)
		// - (com.g2forge.alexandria:ax-java:jar:0.0.7-SNAPSHOT:compile - omitted for duplicate)
		// - com.g2forge.alexandria:ax-type:jar:0.0.7-SNAPSHOT:compile
	}
}
