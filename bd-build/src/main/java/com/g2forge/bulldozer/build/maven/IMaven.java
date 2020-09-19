package com.g2forge.bulldozer.build.maven;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.core.helpers.HStream;
import com.g2forge.gearbox.command.converter.IMethodArgument;
import com.g2forge.gearbox.command.converter.dumb.ArgumentRenderer;
import com.g2forge.gearbox.command.converter.dumb.Command;
import com.g2forge.gearbox.command.converter.dumb.Constant;
import com.g2forge.gearbox.command.converter.dumb.Flag;
import com.g2forge.gearbox.command.converter.dumb.IArgumentRenderer;
import com.g2forge.gearbox.command.converter.dumb.Named;
import com.g2forge.gearbox.command.converter.dumb.Working;
import com.g2forge.gearbox.command.proxy.method.ICommandInterface;

public interface IMaven extends ICommandInterface {
	public static class CSVArgumentRenderer implements IArgumentRenderer<Object> {
		@Override
		public List<String> render(IMethodArgument<Object> argument) {
			final Stream<String> stream;
			final Object value = argument.get();
			if (value instanceof String[]) stream = Stream.of((String[]) value);
			else {
				@SuppressWarnings("unchecked")
				final Collection<String> includes = (Collection<String>) value;
				stream = includes.stream();
			}
			final Named named = argument.getMetadata().get(Named.class);
			return HCollection.asList((named == null ? "" : named.value()) + stream.collect(Collectors.joining(",")));
		}
	}

	public static class SnapshotArgumentRenderer implements IArgumentRenderer<Boolean> {
		@Override
		public List<String> render(IMethodArgument<Boolean> argument) {
			if (argument.get()) return HCollection.asList("versions:use-latest-snapshots", "-DallowSnapshots=true");
			return HCollection.asList("versions:use-latest-releases");
		}
	}

	public static class UpdateParentArgumentRenderer implements IArgumentRenderer<Boolean> {
		@Override
		public List<String> render(IMethodArgument<Boolean> argument) {
			if (argument.get()) return HCollection.asList("versions:update-parent");
			return HCollection.emptyList();
		}
	}

	public static final String SNAPSHOT = "-SNAPSHOT";

	public static final String RELEASE_PROPERTIES = "release.properties";

	@Command({ "mvn", "dependency:tree" })
	public Stream<String> dependencyTree(@Working Path path, @Flag("-Dverbose") boolean verbose, @ArgumentRenderer(CSVArgumentRenderer.class) @Named("-Dincludes=") List<String> includes);

	@Command({ "mvn", "dependency:tree" })
	public Stream<String> dependencyTree(@Working Path path, @Flag("-Dverbose") boolean verbose, @ArgumentRenderer(CSVArgumentRenderer.class) @Named("-Dincludes=") String... includes);

	public default String evaluate(Path path, String expression) {
		return HStream.findOne(evaluateRaw(path, expression).filter(line -> !(line.startsWith("[INFO]") || line.startsWith("[WARNING]") || line.startsWith("[ERROR]") || line.startsWith("Downloading from "))));
	}

	@Command({ "mvn", "help:evaluate" })
	public Stream<String> evaluateRaw(@Working Path path, @Named("-Dexpression=") String expression);

	@Command({ "mvn", "clean", "install", "-Prelease" })
	public void install(@Working Path path);

	@Command({ "mvn", "release:perform", "-Prelease" })
	public void releasePerform(@Working Path path);

	@Command({ "mvn", "--batch-mode", "release:prepare", "-Prelease" })
	public void releasePrepare(@Working Path path, @Named("-Dtag") String tag, @Named("-DreleaseVersion") String release, @Named("-DdevelopmentVersion=") String development);

	@Command({ "mvn" })
	public void updateVersions(@Working Path path, @ArgumentRenderer(UpdateParentArgumentRenderer.class) boolean parent, @ArgumentRenderer(SnapshotArgumentRenderer.class) @Constant("versions:update-properties") boolean snapshot, @ArgumentRenderer(CSVArgumentRenderer.class) @Named("-P") List<String> profiles, @ArgumentRenderer(CSVArgumentRenderer.class) @Named("-Dincludes=") List<String> includes);

	@Command({ "mvn", "clean", "verify" })
	public void verify(@Working Path path);
}
