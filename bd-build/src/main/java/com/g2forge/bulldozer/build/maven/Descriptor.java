package com.g2forge.bulldozer.build.maven;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class Descriptor {
	protected static final Pattern PATTERN = Pattern.compile("([^:]*):([^:]*):([^:]*):([^:]*):([^:]*)");

	public static Descriptor fromString(String string) {
		final Matcher matcher = PATTERN.matcher(string);
		if (!matcher.matches()) throw new IllegalArgumentException();
		final Descriptor.DescriptorBuilder retVal = builder();
		retVal.group(matcher.group(1));
		retVal.artifact(matcher.group(2));
		retVal.packaging(matcher.group(3));
		retVal.version(matcher.group(4));
		retVal.scope(matcher.group(5));
		return retVal.build();
	}

	protected final String group;

	protected final String artifact;

	protected final String packaging;

	protected final String version;

	protected final String scope;

	@Override
	public String toString() {
		final StringBuilder retVal = new StringBuilder();
		retVal.append(getGroup());
		retVal.append(':').append(getArtifact());
		if (getPackaging() != null) retVal.append(':').append(getPackaging());
		retVal.append(':').append(getVersion());
		if (getScope() != null) retVal.append(':').append(getScope());
		return retVal.toString();
	}
}