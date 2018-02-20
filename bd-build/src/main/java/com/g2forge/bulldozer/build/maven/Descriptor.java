package com.g2forge.bulldozer.build.maven;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@JsonInclude(Include.NON_EMPTY)
public class Descriptor implements IDescriptor {
	protected static final Pattern PATTERN = Pattern.compile("([^:]*):([^:]*):([^:]*):([^:]*):([^:]*)");

	public static Descriptor fromString(String string) {
		final Matcher matcher = PATTERN.matcher(string);
		if (!matcher.matches()) throw new IllegalArgumentException();
		final Descriptor.DescriptorBuilder retVal = builder();
		retVal.groupId(matcher.group(1));
		retVal.artifactId(matcher.group(2));
		retVal.packaging(matcher.group(3));
		retVal.version(matcher.group(4));
		retVal.scope(matcher.group(5));
		return retVal.build();
	}

	protected final String groupId;

	protected final String artifactId;

	protected final String packaging;

	protected final String version;

	protected final String scope;

	@Override
	public String toString() {
		final StringBuilder retVal = new StringBuilder();
		retVal.append(getGroupId());
		retVal.append(':').append(getArtifactId());
		if (getPackaging() != null) retVal.append(':').append(getPackaging());
		retVal.append(':').append(getVersion());
		if (getScope() != null) retVal.append(':').append(getScope());
		return retVal.toString();
	}
}