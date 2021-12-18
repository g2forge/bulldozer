package com.g2forge.bulldozer.build.maven;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.g2forge.alexandria.java.core.resource.Resource;
import com.g2forge.alexandria.test.HAssert;
import com.g2forge.bulldozer.build.maven.POM.POMBuilder;
import com.g2forge.bulldozer.build.maven.Profile.ProfileBuilder;
import com.g2forge.bulldozer.build.maven.Repository.Policies;
import com.g2forge.bulldozer.build.maven.distribution.DistributionRepository;
import com.g2forge.bulldozer.build.maven.distribution.DistributionSnapshotRepository;

public class TestPOM {
	@Test
	public void repositories() throws JsonProcessingException {
		final POMBuilder pom = POM.builder();
		{
			final ProfileBuilder profile = Profile.builder().id("profile-id");
			final Policies policies = Repository.Policies.builder().enabled(true).build();
			profile.repository(Repository.builder().id("repository1-id").url("https://example.com/repository1").releases(policies).snapshots(policies).build());
			pom.profile(profile.build());
		}
		pom.repository(Repository.builder().id("repository2-id").url("https://example.com/repository2").build());
		final String actual = POM.getXmlMapper().writeValueAsString(pom.build_());
		HAssert.assertEquals(new Resource(getClass(), "repositories.xml"), actual.replace(System.lineSeparator(), "\n"));
	}

	@Test
	public void distributionManagement() throws JsonProcessingException {
		final POMBuilder pom = POM.builder();
		{
			final ProfileBuilder profile = Profile.builder().id("profile-id");
			profile.distributionManagement(DistributionRepository.builder().id("repository1-id").name("Repository 1 Name").url("https://example.com/repository1").build());
			pom.profile(profile.build());
		}
		pom.distributionManagement(DistributionSnapshotRepository.builder().id("repository2-id").name("Repository 2 Name").url("https://example.com/repository2").build());
		final String actual = POM.getXmlMapper().writeValueAsString(pom.build_());
		HAssert.assertEquals(new Resource(getClass(), "distributionManagement.xml"), actual.replace(System.lineSeparator(), "\n"));
	}
}
