package io.micronaut.scripts.github.project.commands;

import com.github.MoveRequest;
import io.micronaut.scripts.github.project.models.MoveSummary;
import io.micronaut.scripts.github.project.services.ProjectMover;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.annotation.Prototype;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "github-project-issue-move",
        description = "Move issues and pull requests from one GitHub project to another.",
        mixinStandardHelpOptions = true)
@Prototype
public class GithubProjectIssueMoveCommand implements Runnable {

    private final ProjectMover projectMover;

    @Spec
    CommandSpec spec;

    @Option(names = {"-o", "--organization"}, description = "GitHub organization login.", defaultValue = "micronaut-projects")
    String organization;

    @Option(names = {"-s", "--source"}, description = "Source project node ID or organization project number.")
    String sourceProject;

    @Option(names = {"-d", "--destination"}, description = "Destination project node ID or organization project number.")
    String destinationProject;

    @Option(names = {"-t", "--token"}, description = "GitHub token. Defaults to the GITHUB_TOKEN environment variable.")
    String token;

    @Option(names = {"-v", "--verbose"}, description = "Print the resolved projects before moving items.")
    boolean verbose;

    public GithubProjectIssueMoveCommand(ProjectMover projectMover) {
        this.projectMover = projectMover;
    }

    static void main(String[] args) {
        PicocliRunner.run(GithubProjectIssueMoveCommand.class, args);
    }

    @Override
    public void run() {
        MoveRequest request = new MoveRequest(organization, sourceProject, destinationProject, token);
        MoveSummary summary = projectMover.move(request);
        if (verbose) {
            spec.commandLine().getOut().printf("Source: %s (%s)%n", summary.sourceProjectTitle(), summary.sourceProjectId());
            spec.commandLine().getOut().printf("Destination: %s (%s)%n", summary.destinationProjectTitle(), summary.destinationProjectId());
        }
        spec.commandLine().getOut().printf(
                "Scanned %d item(s), moved %d, skipped %d closed or merged item(s), skipped %d unsupported item(s), skipped %d add failure(s).%n",
                summary.scannedItems(),
                summary.movedItems(),
                summary.skippedClosedOrMergedItems(),
                summary.skippedUnsupportedItems(),
                summary.skippedAddFailures());
        for (String message : summary.addFailureMessages()) {
            spec.commandLine().getErr().println(message);
        }
    }
}
