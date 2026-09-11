package io.micronaut.scripts.minorbranch;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.List;
import java.util.Objects;

@ConfigurationProperties("githuborg")
public class GitHubOrgConfiguration {

    private List<String> repos = List.of();

    public List<String> getRepos() {
        return repos;
    }

    public void setRepos(List<String> repos) {
        this.repos = repos == null ? List.of() : repos.stream()
                .filter(Objects::nonNull)
                .map(GitHubOrgConfiguration::stripQuotes)
                .filter(repo -> !repo.isBlank())
                .toList();
    }

    private static String stripQuotes(String value) {
        String stripped = value.trim();
        while (stripped.startsWith("'") || stripped.startsWith("\"")) {
            stripped = stripped.substring(1).trim();
        }
        while (stripped.endsWith("'") || stripped.endsWith("\"")) {
            stripped = stripped.substring(0, stripped.length() - 1).trim();
        }
        return stripped;
    }
}
