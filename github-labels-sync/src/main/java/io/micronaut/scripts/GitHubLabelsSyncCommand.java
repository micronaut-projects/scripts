package io.micronaut.scripts;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

@Command(name = "gitHub-labels-sync",
        description = "Create the standard Micronaut labels in a GitHub repository if they do not already exist.",
        mixinStandardHelpOptions = true)
@Prototype
public class GitHubLabelsSyncCommand implements Callable<Integer> {

    private final LabelsSynchronizer synchronizer;

    @Spec
    CommandSpec spec;

    @Parameters(index = "0", paramLabel = "<repository>", description = "Repository slug, e.g. micronaut-core.")
    String repository;

    @Option(names = {"-o", "--organization"}, description = "GitHub organization login.", defaultValue = "micronaut-projects")
    String organization;

    @Option(names = {"-t", "--token"}, description = "GitHub token. Defaults to the GITHUB_TOKEN environment variable.")
    String token;

    @Option(names = {"-n", "--dry-run"}, description = "Only report which labels would be created.")
    boolean dryRun;

    @Option(names = {"-v", "--verbose"}, description = "Also list the labels that already existed.")
    boolean verbose;

    public GitHubLabelsSyncCommand(LabelsSynchronizer synchronizer) {
        this.synchronizer = synchronizer;
    }

    public static void main(String[] args) {
        System.exit(PicocliRunner.execute(GitHubLabelsSyncCommand.class, args));
    }

    @Override
    public Integer call() {
        String resolvedToken = StringUtils.isNotEmpty(token) ? token : System.getenv("GITHUB_TOKEN");
        if (StringUtils.isEmpty(resolvedToken)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "Missing GitHub token: pass --token or set the GITHUB_TOKEN environment variable.");
        }
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        out.println("Repository: " + organization + "/" + repository + (dryRun ? " (dry run)" : ""));
        try {
            SyncSummary summary = synchronizer.sync(new SyncRequest(organization, repository, resolvedToken, dryRun));
            for (String name : summary.created()) {
                out.println((dryRun ? "Would create label '" : "Created label '") + name + "'");
            }
            if (verbose) {
                for (String name : summary.skipped()) {
                    out.println("Skipped existing label '" + name + "'");
                }
            }
            out.println((dryRun ? "Would create " : "Created ") + summary.created().size()
                    + " label(s), skipped " + summary.skipped().size() + " existing label(s).");
            return CommandLine.ExitCode.OK;
        } catch (HttpClientResponseException e) {
            err.println("GitHub API request failed with status " + e.getStatus().getCode() + ": " + e.getMessage());
            return CommandLine.ExitCode.SOFTWARE;
        }
    }
}
