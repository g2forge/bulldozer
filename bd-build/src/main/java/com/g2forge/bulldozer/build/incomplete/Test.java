package com.g2forge.bulldozer.build.incomplete;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.gearbox.functional.proxy.Proxifier;
import com.g2forge.gearbox.functional.runner.ProcessBuilderRunner;

public class Test {
	public static void main(String[] args) throws JsonParseException, JsonMappingException, IOException {
		final Path path = Paths.get(args[0]).resolve("enigma");
		final IMaven maven = new Proxifier().generate(new ProcessBuilderRunner("cmd", "/C"), IMaven.class);
		final Map<String, List<Descriptor>> grouped = maven.mvn(path, true, "dependency:tree", "-Dincludes=com.g2forge.*:*")/*.map(new TapFunction<>(System.out::println))*/.filter(line -> line.startsWith("[INFO]") && line.contains("- com.g2forge.")).map(line -> Descriptor.fromString(line.substring(line.indexOf("- ") + 2))).collect(Collectors.groupingBy(Descriptor::getGroup));
		final Map<String, String> versions = new LinkedHashMap<>();
		for (List<Descriptor> group : grouped.values()) {
			final Set<String> groupVersions = group.stream().map(Descriptor::getVersion).collect(Collectors.toSet());
			if (groupVersions.size() > 1) throw new IllegalArgumentException(String.format("Depended on multiple versions of the project \"%1$s\": %2$s", group.get(0).getGroup(), groupVersions));
			versions.put(group.get(0).getGroup(), HCollection.getOne(groupVersions));
		}
		System.out.println(versions);

		// Do something to figure out what OS we're on the generate the shell prefix (bash vs cmd)

		// Do something smarter and more type safe for running maven to get dependency trees (includes is a csv list, and the only argument, verbose should be a
		// flag, the goal should be hard-coded)
	}
}
