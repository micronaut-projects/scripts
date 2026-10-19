package io.micronaut.scripts;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubLabelsSyncCommandTest {

    @Test
    void commandBuildsSyncRequestAndReportsSummary() {
        RecordingSynchronizer synchronizer = new RecordingSynchronizer(
                new SyncSummary(List.of("type: bug"), List.of("type: docs")));
        Execution execution = execute(synchronizer, "--token", "github-token", "--verbose", "micronaut-core");

        assertEquals(CommandLine.ExitCode.OK, execution.exitCode());
        assertEquals(new SyncRequest("micronaut-projects", "micronaut-core", "github-token", false), synchronizer.request);
        assertTrue(execution.out().contains("Repository: micronaut-projects/micronaut-core"));
        assertTrue(execution.out().contains("Created label 'type: bug'"));
        assertTrue(execution.out().contains("Skipped existing label 'type: docs'"));
        assertTrue(execution.out().contains("Created 1 label(s), skipped 1 existing label(s)."));
    }

    @Test
    void organizationAndDryRunCanBeOverridden() {
        RecordingSynchronizer synchronizer = new RecordingSynchronizer(new SyncSummary(List.of("type: bug"), List.of()));
        Execution execution = execute(synchronizer, "-t", "github-token", "-o", "my-org", "--dry-run", "my-repo");

        assertEquals(CommandLine.ExitCode.OK, execution.exitCode());
        assertEquals(new SyncRequest("my-org", "my-repo", "github-token", true), synchronizer.request);
        assertTrue(execution.out().contains("Would create label 'type: bug'"));
        assertFalse(execution.out().contains("Skipped existing"));
    }

    @Test
    void repositoryIsRequired() {
        RecordingSynchronizer synchronizer = new RecordingSynchronizer(new SyncSummary(List.of(), List.of()));
        Execution execution = execute(synchronizer, "--token", "github-token");

        assertEquals(CommandLine.ExitCode.USAGE, execution.exitCode());
        assertNull(synchronizer.request);
        assertTrue(execution.err().contains("Missing required parameter: '<repository>'"));
    }

    private static Execution execute(LabelsSynchronizer synchronizer, String... args) {
        CommandLine commandLine = new CommandLine(new GitHubLabelsSyncCommand(synchronizer));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        commandLine.setOut(new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true));
        commandLine.setErr(new PrintWriter(new OutputStreamWriter(err, StandardCharsets.UTF_8), true));
        int exitCode = commandLine.execute(args);
        return new Execution(exitCode, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Execution(int exitCode, String out, String err) {
    }

    private static final class RecordingSynchronizer implements LabelsSynchronizer {
        private final SyncSummary summary;
        private SyncRequest request;

        private RecordingSynchronizer(SyncSummary summary) {
            this.summary = summary;
        }

        @Override
        public SyncSummary sync(SyncRequest request) {
            this.request = request;
            return summary;
        }
    }
}
