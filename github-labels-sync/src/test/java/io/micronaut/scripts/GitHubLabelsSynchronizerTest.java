package io.micronaut.scripts;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubLabelsSynchronizerTest {

    private static final String SPEC_NAME = "GitHubLabelsSynchronizerTest";

    @Test
    void createsOnlyMissingLabels() {
        int port = SocketUtils.findAvailableTcpPort();
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
                "spec.name", SPEC_NAME,
                "micronaut.server.port", port,
                "micronaut.http.services.github.url", "http://localhost:" + port))) {
            FakeGitHub gitHub = server.getApplicationContext().getBean(FakeGitHub.class);
            // Existing label names differ in case: GitHub treats label names case-insensitively.
            gitHub.labels.add(new GitHubLabel("Type: Bug", "d73a4a", "Something isn't working"));
            gitHub.labels.add(new GitHubLabel("closed: duplicate", "cfd3d7", null));
            List<Label> configured = new ArrayList<>(server.getApplicationContext().getBeansOfType(Label.class));
            LabelsSynchronizer synchronizer = server.getApplicationContext().getBean(LabelsSynchronizer.class);

            SyncSummary summary = synchronizer.sync(new SyncRequest("micronaut-projects", "micronaut-core", "secret", false));

            assertEquals(List.of("type: bug", "closed: duplicate"), sorted(summary.skipped()));
            assertEquals(configured.size() - 2, summary.created().size());
            assertEquals(configured.size() - 2, gitHub.created.size());
            assertEquals(configured.size(), gitHub.labels.size());
            assertEquals(List.of("Bearer secret"), gitHub.authorizations.stream().distinct().toList());
            GitHubLabel majorChange = gitHub.created.stream().filter(l -> l.name().equals("major change")).findFirst().orElseThrow();
            assertEquals("eb5a46", majorChange.color());
            assertEquals("This item represents a major change in functionality and must be voted on before merging", majorChange.description());
            assertTrue(gitHub.created.stream().noneMatch(l -> l.name().equalsIgnoreCase("type: bug")));
        }
    }

    @Test
    void dryRunDoesNotCreateLabels() {
        int port = SocketUtils.findAvailableTcpPort();
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
                "spec.name", SPEC_NAME,
                "micronaut.server.port", port,
                "micronaut.http.services.github.url", "http://localhost:" + port))) {
            FakeGitHub gitHub = server.getApplicationContext().getBean(FakeGitHub.class);
            LabelsSynchronizer synchronizer = server.getApplicationContext().getBean(LabelsSynchronizer.class);

            SyncSummary summary = synchronizer.sync(new SyncRequest("micronaut-projects", "micronaut-core", "secret", true));

            assertFalse(summary.created().isEmpty());
            assertTrue(summary.skipped().isEmpty());
            assertTrue(gitHub.created.isEmpty());
        }
    }

    private static List<String> sorted(List<String> names) {
        return names.stream().map(String::toLowerCase).sorted((a, b) -> b.compareTo(a)).toList();
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/repos/{owner}/{repo}/labels")
    @Produces("application/vnd.github+json")
    @Singleton
    static class FakeGitHub {
        final List<GitHubLabel> labels = new CopyOnWriteArrayList<>();
        final List<GitHubLabel> created = new CopyOnWriteArrayList<>();
        final List<String> authorizations = new CopyOnWriteArrayList<>();

        @Get
        List<GitHubLabel> list(@Header(HttpHeaders.AUTHORIZATION) String authorization,
                               @PathVariable String owner,
                               @PathVariable String repo,
                               @QueryValue(defaultValue = "1") int page,
                               @QueryValue(value = "per_page", defaultValue = "30") int perPage) {
            authorizations.add(authorization);
            int from = Math.min((page - 1) * perPage, labels.size());
            int to = Math.min(from + perPage, labels.size());
            return labels.subList(from, to);
        }

        @Post
        HttpResponse<GitHubLabel> create(@Header(HttpHeaders.AUTHORIZATION) String authorization,
                                         @PathVariable String owner,
                                         @PathVariable String repo,
                                         @Body GitHubLabel label) {
            authorizations.add(authorization);
            created.add(label);
            labels.add(label);
            return HttpResponse.status(HttpStatus.CREATED).body(label);
        }
    }
}
