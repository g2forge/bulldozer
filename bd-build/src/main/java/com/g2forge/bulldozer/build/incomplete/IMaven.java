package com.g2forge.bulldozer.build.incomplete;

import java.nio.file.Path;
import java.util.stream.Stream;

import com.g2forge.gearbox.functional.control.Command;
import com.g2forge.gearbox.functional.control.Constant;
import com.g2forge.gearbox.functional.control.Flag;
import com.g2forge.gearbox.functional.control.Working;

public interface IMaven {
	@Command("mvn")
	public Stream<String> dependencyTree(@Working Path path, @Flag("-Dverbose") @Constant("dependency:tree") boolean verbose, String... goals);
}
