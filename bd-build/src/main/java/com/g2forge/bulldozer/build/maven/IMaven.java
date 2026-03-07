package com.g2forge.bulldozer.build.maven;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.core.helpers.HStream;
import com.g2forge.gearbox.command.converter.IMethodArgument;
import com.g2forge.gearbox.command.converter.argumentrenderer.ASimpleArgumentRenderer;
import com.g2forge.gearbox.command.converter.argumentrenderer.ArgumentRenderer;
import com.g2forge.gearbox.command.converter.argumentrenderer.CSVArgumentRenderer;
import com.g2forge.gearbox.command.converter.dumb.Command;
import com.g2forge.gearbox.command.converter.dumb.Constant;
import com.g2forge.gearbox.command.converter.dumb.Flag;
import com.g2forge.gearbox.command.converter.dumb.Named;
import com.g2forge.gearbox.command.converter.dumb.Working;
import com.g2forge.gearbox.command.proxy.method.ICommandInterface;

public interface IMaven extends ICommandInterface {
	public static class SnapshotArgumentRenderer extends ASimpleArgumentRenderer<Boolean> {
		@Override
		protected List<String> renderSimple(IMethodArgument<Boolean> argument) {
			if (argument.get()) return HCollection.asList("versions:use-latest-snapshots", "-DallowSnapshots=true");
			return HCollection.asList("versions:use-latest-releases");
		}
	}

	public static class UpdateParentArgumentRenderer extends ASimpleArgumentRenderer<Boolean> {
		@Override
		protected List<String> renderSimple(IMethodArgument<Boolean> argument) {
			if (argument.get()) return HCollection.asList("versions:update-parent");
			return HCollection.emptyList();
		}
	}

	public static final List<String> PROFILES_RELEASE = HCollection.asList("release", "release-actual");

	public static final String SNAPSHOT = "-SNAPSHOT";

	public static final String RELEASE_PROPERTIES = "release.properties";

	@Command({ "mvn", "dependency:tree" })
	public Stream<String> dependencyTree(@Working Path path, @Flag("-Dverbose") boolean verbose, @ArgumentRenderer(CSVArgumentRenderer.class) @Named("-Dincludes=") List<String> includes);

	@Command({ "mvn", "dependency:tree" })
	public Stream<String> dependencyTree(@Working Path path, @Flag("-Dverbose") boolean verbose, @ArgumentRenderer(CSVArgumentRenderer.class) @Named("-Dincludes=") String... includes);

	public default String evaluate(Path path, String expression) {
		try {
			return HStream.findOne(evaluateRaw(path, expression).filter(line -> !(line.startsWith("[INFO]") || line.startsWith("[WARNING]") || line.startsWith("[ERROR]") || line.startsWith("Downloading from "))));
		} catch (Throwable throwable) {
			throw new RuntimeException(String.format("Failed to evaluate \"%2$s\" in %1$s", path, expression), throwable);
		}
	}

	@Command({ "mvn", "help:evaluate" })
	public Stream<String> evaluateRaw(@Working Path path, @Named("-Dexpression=") String expression);

	@Command({ "mvn", "clean", "install" })
	public void install(@Working Path path, @Flag("-DskipTests") boolean skipTests, @ArgumentRenderer(CSVArgumentRenderer.class) @Named("-P") List<String> profiles);

	@Command({ "mvn", "release:perform" })
	public void releasePerform(@Working Path path, @ArgumentRenderer(CSVArgumentRenderer.class) @Named("-P") List<String> profiles);

	@Command({ "mvn", "--batch-mode", "release:prepare" })
	public void releasePrepare(@Working Path path, @Named("-Dtag=") String tag, @Named("-DreleaseVersion=") String release, @Named("-DdevelopmentVersion=") String development, @ArgumentRenderer(CSVArgumentRenderer.class) @Named("-P") List<String> profiles);

	@Command({ "mvn" })
	public void updateVersions(@Working Path path, @ArgumentRenderer(UpdateParentArgumentRenderer.class) boolean parent, @ArgumentRenderer(SnapshotArgumentRenderer.class) @Constant("versions:update-properties") boolean snapshot, @ArgumentRenderer(CSVArgumentRenderer.class) @Named("-P") List<String> profiles, @ArgumentRenderer(CSVArgumentRenderer.class) @Named("-Dincludes=") List<String> includes);

	@Command({ "mvn", "clean", "verify" })
	public void verify(@Working Path path);
}
