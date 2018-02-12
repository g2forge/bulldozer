package com.g2forge.bulldozer.build.incomplete;

import java.nio.file.Path;
import java.util.stream.Stream;

import com.g2forge.gearbox.functional.control.Flag;
import com.g2forge.gearbox.functional.control.Working;

public interface IMaven {
	public Stream<String> mvn(@Working Path path, @Flag("-Dverbose") boolean verbose, String... goals);
}
