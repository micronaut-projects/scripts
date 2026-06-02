package github.project.issue.move;

import com.github.MoveRequest;
import io.micronaut.scripts.github.project.commands.GithubProjectIssueMoveCommand;
import io.micronaut.scripts.github.project.models.MoveSummary;
import io.micronaut.scripts.github.project.services.ProjectMover;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GithubProjectIssueMoveCommandTest {

    @Test
    void commandBuildsMoveRequest() {
        RecordingProjectMover mover = new RecordingProjectMover(new MoveSummary(
                "PVT_source",
                "Source Project",
                "PVT_destination",
                "Destination Project",
                4,
                2,
                1,
                1,
                1,
                List.of("Could not add PullRequest to destination project.")));
        GithubProjectIssueMoveCommand command = new GithubProjectIssueMoveCommand(mover);
        CommandLine commandLine = new CommandLine(command);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        commandLine.setOut(new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true));
        commandLine.setErr(new PrintWriter(new OutputStreamWriter(error, StandardCharsets.UTF_8), true));

        int exitCode = commandLine.execute(
                "--token", "github-token",
                "--verbose",
                "--organization",
                "micronaut-projects",
                "--source",
                "1",
                "--destination",
                "2");

        assertEquals(CommandLine.ExitCode.OK, exitCode);
        assertEquals(new MoveRequest("micronaut-projects", "1", "2", "github-token"), mover.request);
        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Source: Source Project (PVT_source)"));
        assertTrue(text.contains("Destination: Destination Project (PVT_destination)"));
        assertTrue(text.contains("Scanned 4 item(s), moved 2, skipped 1 closed or merged item(s), skipped 1 unsupported item(s), skipped 1 add failure(s)."));
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("Could not add PullRequest to destination project."));
    }

    private static final class RecordingProjectMover implements ProjectMover {
        private final MoveSummary summary;
        private MoveRequest request;

        private RecordingProjectMover(MoveSummary summary) {
            this.summary = summary;
        }

        @Override
        public MoveSummary move(MoveRequest request) {
            this.request = request;
            return summary;
        }
    }
}
