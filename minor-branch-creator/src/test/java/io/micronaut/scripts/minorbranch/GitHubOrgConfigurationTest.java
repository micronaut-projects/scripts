package io.micronaut.scripts.minorbranch;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class GitHubOrgConfigurationTest {

    @Inject
    GitHubOrgConfiguration configuration;

    @Test
    void micronautSecurityIsConfiguredRepository() {
        assertTrue(configuration.getRepos().contains("micronaut-security"));
    }
}
