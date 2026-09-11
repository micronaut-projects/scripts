package com.example;

import com.example.service.GitHubActionsService;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "gh-actions-cancel",
    description = "Cancel all queued and in-progress GitHub Actions workflow runs for a repository.",
    mixinStandardHelpOptions = true,
    version = "1.0"
)
public class GithubActionsCancellerCommand implements Runnable {

    @Option(
            names = {"-o", "--owner"},
            description = "Repository owner",
            defaultValue = "micronaut-projects"
    )
    private String owner;

    @Option(
            names = {"-r", "--repository"},
            description = "Repository",
            required = true
    )
    private String repository;

    @Option(
        required = true,
        names = {"-t", "--token"},
        description = "GitHub personal access token. E.g. a classic token with repo and workflow scopes",
        defaultValue = "${GITHUB_TOKEN}"
    )
    private String token;

    @Option(
        names = {"-d", "--dry-run"},
        description = "List runs that would be cancelled without actually cancelling them.",
        defaultValue = "false"
    )
    private boolean dryRun;

    @Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose output.",
        defaultValue = "false"
    )
    private boolean verbose;

    @Inject
    private GitHubActionsService actionsService;

    public static void main(String[] args) {
        int exitCode = PicocliRunner.execute(GithubActionsCancellerCommand.class, args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        if (StringUtils.isEmpty(token) || token.equals("${GITHUB_TOKEN}")) {
            System.err.println("Error: GitHub token is required.");
            System.err.println("Provide it via --token option or GITHUB_TOKEN environment variable.");
            System.exit(1);
        }

        System.out.println("Repository: " + owner + "/" + repository);
        
        if (dryRun) {
            System.out.println("DRY RUN MODE - no runs will be cancelled");
            System.out.println();
            listCancellableRuns(owner, repository);
        } else {
            System.out.println();
            cancelAllRuns(owner, repository);
        }
    }

    private void listCancellableRuns(String owner, String repo) {
        try {
            var runs = actionsService.getCancellableRuns(owner, repo, token);
            
            if (runs.isEmpty()) {
                System.out.println("No cancellable workflow runs found.");
                return;
            }
            
            System.out.println("Found " + runs.size() + " cancellable workflow run(s):");
            System.out.println();
            
            for (var run : runs) {
                System.out.printf("  #%-6d %-40s [%-12s] branch: %s%n",
                    run.runNumber(),
                    truncate(run.name(), 40),
                    run.status(),
                    run.headBranch()
                );
                if (verbose) {
                    System.out.println("           URL: " + run.htmlUrl());
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching workflow runs: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    private void cancelAllRuns(String owner, String repo) {
        try {
            int cancelled = actionsService.cancelAllRuns(owner, repo, token, System.out::println);
            System.out.println();
            System.out.println("Done! Cancelled " + cancelled + " workflow run(s).");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}
