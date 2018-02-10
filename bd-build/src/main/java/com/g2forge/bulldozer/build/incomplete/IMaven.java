package com.g2forge.bulldozer.build.incomplete;

import java.nio.file.Path;

import com.g2forge.gearbox.functional.control.Flag;
import com.g2forge.gearbox.functional.control.Working;

public interface IMaven {
	public String mvn(@Working Path path, @Flag("-Dverbose") boolean verbose, String... goals);
}
