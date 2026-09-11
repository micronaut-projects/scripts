package io.micronaut.scripts.minorbranch;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MinorBranchCreatorCommandTest {

    @Test
    void commandCanBeLoadedFromMicronautContext() {
        try (ApplicationContext context = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            assertTrue(context.containsBean(MinorBranchCreatorCommand.class));
        }
    }

    @Test
    void bindsGitHubOrgRepositoriesFromConfiguration() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
                "githuborg.repos", List.of("'micronaut-security'", "micronaut-data'")
        ), Environment.TEST)) {
            GitHubOrgConfiguration configuration = context.getBean(GitHubOrgConfiguration.class);

            assertEquals(List.of("micronaut-security", "micronaut-data"), configuration.getRepos());
        }
    }

    @Test
    void createsBranchAndUpdatesDefaultBranch() {
        FakeGitHubOperations operations = new FakeGitHubOperations("""
                [versions]
                managed-micronaut-security = "5.0.4"
                """);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(operations, out, err);

        int exitCode = commandLine.execute(
                "--url", "https://github.com/micronaut-projects/micronaut-platform/blob/5.0.x/gradle/libs.versions.toml",
                "--repository", "micronaut-projects/micronaut-security",
                "--github-token", "secret",
                "--set-default");

        assertEquals(0, exitCode, err.toString());
        assertEquals("micronaut-projects/micronaut-security", operations.repository.toString());
        assertEquals("secret", operations.token);
        assertEquals("5.1.x", operations.createdBranch);
        assertEquals("abc123", operations.createdSha);
        assertEquals("5.1.x", operations.updatedDefaultBranch);
        assertEquals(1, operations.createBranchCallCount);
        assertEquals(1, operations.updateDefaultBranchCallCount);
        assertTrue(out.toString().contains("Branch: created"));
        assertTrue(out.toString().contains("Default branch: updated"));
    }

    @Test
    void dryRunReportsActionsWithoutChangingGitHub() {
        FakeGitHubOperations operations = new FakeGitHubOperations("""
                [versions]
                managed-micronaut-security = "5.0.4"
                """);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(operations, out, err);

        int exitCode = commandLine.execute(
                "-u", "https://github.com/micronaut-projects/micronaut-platform/blob/5.0.x/gradle/libs.versions.toml",
                "--repository", "micronaut-projects/micronaut-security",
                "--github-token", "secret",
                "--set-default",
                "--dry-run");

        assertEquals(0, exitCode, err.toString());
        assertNull(operations.createdBranch);
        assertNull(operations.updatedDefaultBranch);
        assertEquals(0, operations.createBranchCallCount);
        assertEquals(0, operations.updateDefaultBranchCallCount);
        assertTrue(out.toString().contains("Mode: dry run"));
        assertTrue(out.toString().contains("Branch: would be created"));
        assertTrue(out.toString().contains("Default branch: would be updated"));
        assertTrue(out.toString().contains("Create branch action: refs/heads/5.1.x from 5.0.x"));
        assertTrue(out.toString().contains("Set default branch action: 5.1.x"));
    }

    @Test
    void loopsOverConfiguredRepositoriesWhenRepositoryOptionIsMissing() {
        FakeGitHubOperations operations = new FakeGitHubOperations("""
                [versions]
                managed-micronaut-security = "5.0.4"
                managed-micronaut-data = "4.0.1"
                """);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(operations, configuration("micronaut-security", "micronaut-data"), out, err);

        int exitCode = commandLine.execute(
                "--url", "https://github.com/micronaut-projects/micronaut-platform/blob/5.0.x/gradle/libs.versions.toml",
                "--github-token", "secret",
                "--dry-run");

        assertEquals(0, exitCode, err.toString());
        assertEquals(List.of(
                "micronaut-projects/micronaut-security",
                "micronaut-projects/micronaut-data"
        ), operations.repositories.stream().map(GitHubRepository::toString).distinct().toList());
        assertEquals(0, operations.createBranchCallCount);
        assertEquals(0, operations.updateDefaultBranchCallCount);
        assertTrue(out.toString().contains("Repositories: 2"));
        assertTrue(out.toString().contains("Repository: micronaut-projects/micronaut-security"));
        assertTrue(out.toString().contains("Repository: micronaut-projects/micronaut-data"));
    }

    @Test
    void skipsBranchCreationWhenTargetBranchAlreadyExists() {
        FakeGitHubOperations operations = new FakeGitHubOperations("""
                [versions]
                micronaut-security = "5.0.4"
                """);
        operations.branchExists = true;

        MinorBranchResult result = new MinorBranchCreator(operations).create(
                URI.create("https://example.com/libs.versions.toml"),
                GitHubRepository.parse("micronaut-projects/micronaut-security"),
                true,
                false,
                "secret");

        assertFalse(result.branchCreationPlanned());
        assertFalse(result.branchCreated());
        assertTrue(result.defaultBranchUpdatePlanned());
        assertTrue(result.defaultBranchUpdated());
        assertNull(operations.createdBranch);
        assertEquals("5.1.x", operations.updatedDefaultBranch);
    }

    @Test
    void derivesTheNextMinorBranchFromSemanticVersion() {
        assertEquals("5.1.x", MinorBranchName.nextMinorBranch("5.0.4"));
        assertEquals("2.13.x", MinorBranchName.nextMinorBranch("2.12.0-SNAPSHOT"));
        assertThrows(MinorBranchCreatorException.class, () -> MinorBranchName.nextMinorBranch("5.0"));
    }

    @Test
    void findsRepositoryVersionInVersionsToml() {
        String toml = """
                [versions]
                other = "1.0.0"
                managed-micronaut-security = "5.0.4" # comment
                [libraries]
                micronaut-security = { module = "io.micronaut.security:micronaut-security" }
                """;

        assertEquals("5.0.4", VersionsToml.findVersion(toml, "micronaut-security").orElseThrow());
    }

    @Test
    void findsRepositoryVersionForTomlKeyExceptions() {
        String toml = """
                [versions]
                managed-micronaut-problem = "3.0.1"
                managed-micronaut-mongo = "4.2.0"
                managed-micronaut-oraclecloud = "5.1.3"
                managed-micronaut-discovery = "6.0.2"
                """;

        assertEquals("3.0.1", VersionsToml.findVersion(toml, "micronaut-problem-json").orElseThrow());
        assertEquals("4.2.0", VersionsToml.findVersion(toml, "micronaut-mongodb").orElseThrow());
        assertEquals("5.1.3", VersionsToml.findVersion(toml, "micronaut-oracle-cloud").orElseThrow());
        assertEquals("6.0.2", VersionsToml.findVersion(toml, "micronaut-discovery-client").orElseThrow());
    }

    @Test
    void parsesGitHubBlobUrl() {
        GitHubFile file = GitHubFile.fromBlobUrl(URI.create(
                "https://github.com/micronaut-projects/micronaut-platform/blob/5.0.x/gradle/libs.versions.toml"))
                .orElseThrow();

        assertEquals("micronaut-projects/micronaut-platform", file.repository().toString());
        assertEquals("5.0.x", file.ref());
        assertEquals("gradle/libs.versions.toml", file.path());
    }

    private static CommandLine commandLine(FakeGitHubOperations operations, ByteArrayOutputStream out, ByteArrayOutputStream err) {
        return commandLine(operations, configuration(), out, err);
    }

    private static CommandLine commandLine(FakeGitHubOperations operations,
                                           GitHubOrgConfiguration configuration,
                                           ByteArrayOutputStream out,
                                           ByteArrayOutputStream err) {
        CommandLine commandLine = new CommandLine(new MinorBranchCreatorCommand(new MinorBranchCreator(operations), configuration));
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));
        return commandLine;
    }

    private static GitHubOrgConfiguration configuration(String... repos) {
        GitHubOrgConfiguration configuration = new GitHubOrgConfiguration();
        configuration.setRepos(List.of(repos));
        return configuration;
    }

    private static final class FakeGitHubOperations implements GitHubOperations {
        private final String toml;
        private final List<GitHubRepository> repositories = new ArrayList<>();
        private boolean branchExists;
        private GitHubRepository repository;
        private String token;
        private String createdBranch;
        private String createdSha;
        private String updatedDefaultBranch;
        private int createBranchCallCount;
        private int updateDefaultBranchCallCount;

        private FakeGitHubOperations(String toml) {
            this.toml = toml;
        }

        @Override
        public GitHubRepositoryState repositoryState(GitHubRepository repository, String token) {
            capture(repository, token);
            return new GitHubRepositoryState("5.0.x");
        }

        @Override
        public String fetchText(URI url, String token) {
            this.token = token;
            return toml;
        }

        @Override
        public BranchRef branchRef(GitHubRepository repository, String branch, String token) {
            capture(repository, token);
            assertEquals("5.0.x", branch);
            return new BranchRef("abc123");
        }

        @Override
        public boolean branchExists(GitHubRepository repository, String branch, String token) {
            capture(repository, token);
            return branchExists;
        }

        @Override
        public void createBranch(GitHubRepository repository, String branch, String sha, String token) {
            capture(repository, token);
            createBranchCallCount++;
            createdBranch = branch;
            createdSha = sha;
        }

        @Override
        public void updateDefaultBranch(GitHubRepository repository, String branch, String token) {
            capture(repository, token);
            updateDefaultBranchCallCount++;
            updatedDefaultBranch = branch;
        }

        private void capture(GitHubRepository repository, String token) {
            this.repositories.add(repository);
            this.repository = repository;
            this.token = token;
        }
    }
}
