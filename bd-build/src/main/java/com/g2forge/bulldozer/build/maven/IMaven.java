package com.g2forge.bulldozer.build.maven;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.g2forge.alexandria.java.core.helpers.HStream;
import com.g2forge.alexandria.java.typed.ATypeRef;
import com.g2forge.gearbox.functional.control.Command;
import com.g2forge.gearbox.functional.control.Constant;
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
	public static class CSVArgumentHandler implements IExplicitArgumentHandler {
		@Override
		public void accept(IArgumentContext context, Object argument) {
			final Stream<String> stream;
			if (argument instanceof String[]) stream = Stream.of((String[]) argument);
			else {
				@SuppressWarnings("unchecked")
				final List<String> includes = (List<String>) argument;
				stream = includes.stream();
			}
			final Named named = context.getArgument().getAnnotation(Named.class);
			final String string = (named == null ? "" : named.value()) + stream.collect(Collectors.joining(","));
			context.getCommand().argument(string);
		}
	}

	public static class EvaluateResultHandler implements IExplicitResultHandler {
		@Override
		public Object apply(IProcess proccess, IResultContext context) {
			final IExplicitResultHandler handler = context.getStandard(new ATypeRef<Stream<String>>() {});
			@SuppressWarnings("unchecked")
			final Stream<String> stream = ((Stream<String>) handler.apply(proccess, context)).filter(line -> !(line.startsWith("[INFO]") || line.startsWith("[WARNING]") || line.startsWith("[ERROR]")));
			return HStream.findOne(stream);
		}
	}

	public static class SnapshotArgumentHandler implements IExplicitArgumentHandler {
		@Override
		public void accept(IArgumentContext context, Object argument) {
			final boolean snapshots = (Boolean) argument;
			context.getCommand().argument(snapshots ? "versions:use-latest-snapshots" : "versions:use-latest-releases");
			if (snapshots) context.getCommand().argument("-DallowSnapshots=true");
		}
	}

	public static String RELEASE_PROPERTIES = "release.properties";

	public static String POM_XML = "pom.xml";

	@Command({ "mvn", "dependency:tree" })
	public Stream<String> dependencyTree(@Working Path path, @Flag("-Dverbose") boolean verbose, @Explicit(CSVArgumentHandler.class) @Named("-Dincludes=") List<String> includes);

	@Command({ "mvn", "dependency:tree" })
	public Stream<String> dependencyTree(@Working Path path, @Flag("-Dverbose") boolean verbose, @Explicit(CSVArgumentHandler.class) @Named("-Dincludes=") String... includes);

	@Command(value = { "mvn", "help:evaluate" }, handler = EvaluateResultHandler.class)
	public String evaluate(@Working Path path, @Named("-Dexpression=") String expression);

	@Command({ "mvn", "clean", "install", "-Prelease" })
	public void install(@Working Path path);

	@Command({ "mvn", "release:perform", "-Prelease" })
	public void releasePerform(@Working Path path);

	@Command({ "mvn", "--batch-mode", "release:prepare", "-Prelease" })
	public void releasePrepare(@Working Path path, @Named("-Dtag") String tag, @Named("-DreleaseVersion") String release, @Named("-DdevelopmentVersion=") String development);

	@Command({ "mvn", "versions:update-parent" })
	public void updateVersions(@Working Path path, @Explicit(SnapshotArgumentHandler.class) @Constant("versions:update-properties") boolean snapshot, @Explicit(CSVArgumentHandler.class) @Named("-P") List<String> profiles, @Explicit(CSVArgumentHandler.class) @Named("-Dincludes=") List<String> includes);
}
