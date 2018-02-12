package com.g2forge.bulldozer.build.maven;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.g2forge.alexandria.java.core.helpers.HStream;
import com.g2forge.alexandria.java.typed.ATypeRef;
import com.g2forge.gearbox.functional.control.Command;
import com.g2forge.gearbox.functional.control.Explicit;
import com.g2forge.gearbox.functional.control.Flag;
import com.g2forge.gearbox.functional.control.IArgumentContext;
import com.g2forge.gearbox.functional.control.IExplicitArgumentHandler;
import com.g2forge.gearbox.functional.control.IExplicitResultHandler;
import com.g2forge.gearbox.functional.control.IResultContext;
import com.g2forge.gearbox.functional.control.Named;
import com.g2forge.gearbox.functional.control.Working;
import com.g2forge.gearbox.functional.runner.IProcess;

public interface IMaven {
	public static class EvaluateResultHandler implements IExplicitResultHandler {
		@Override
		public Object apply(IProcess proccess, IResultContext context) {
			final IExplicitResultHandler handler = context.getStandard(new ATypeRef<Stream<String>>() {});
			@SuppressWarnings("unchecked")
			final Stream<String> stream = ((Stream<String>) handler.apply(proccess, context)).filter(line -> !(line.startsWith("[INFO]") || line.startsWith("[WARNING]") || line.startsWith("[ERROR]")));
			return HStream.findOne(stream);
		}
	}

	public static class IncludesArgumentHandler implements IExplicitArgumentHandler {
		@Override
		public void accept(IArgumentContext context, Object argument) {
			final Stream<String> stream;
			if (argument instanceof String[]) stream = Stream.of((String[]) argument);
			else {
				@SuppressWarnings("unchecked")
				final List<String> includes = (List<String>) argument;
				stream = includes.stream();
			}
			context.getCommand().argument("-Dincludes=" + stream.collect(Collectors.joining(",")));
		}
	}

	@Command({ "mvn", "dependency:tree" })
	public Stream<String> dependencyTree(@Working Path path, @Flag("-Dverbose") boolean verbose, @Explicit(IncludesArgumentHandler.class) List<String> goals);

	@Command({ "mvn", "dependency:tree" })
	public Stream<String> dependencyTree(@Working Path path, @Flag("-Dverbose") boolean verbose, @Explicit(IncludesArgumentHandler.class) String... goals);

	@Command(value = { "mvn", "org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate" }, handler = EvaluateResultHandler.class)
	public String evaluate(@Working Path path, @Named("-Dexpression=") String expression);

	@Command({ "mvn", "install", "-Prelease" })
	public void install(@Working Path path);

	@Command({ "mvn", "release:perform", "-Prelease" })
	public void releasePerform(@Working Path path);

	@Command({ "mvn", "release:prepare", "-Prelease" })
	public void releasePrepare(@Working Path path);
}
