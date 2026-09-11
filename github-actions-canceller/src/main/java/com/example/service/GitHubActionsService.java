package com.example.service;

import com.example.client.GitHubActionsClient;
import com.example.dto.WorkflowRun;
import com.example.dto.WorkflowRunsResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Singleton
public class GitHubActionsService {

    private final GitHubActionsClient client;

    public GitHubActionsService(GitHubActionsClient client) {
        this.client = client;
    }

    /**
     * Fetches all cancellable workflow runs (queued, in_progress, pending, waiting).
     */
    public List<WorkflowRun> getCancellableRuns(String owner, String repo, String token) {
        String auth = "Bearer " + token;
        List<WorkflowRun> allRuns = new ArrayList<>();
        
        // Fetch runs with different statuses that can be cancelled
        for (String status : List.of("queued", "in_progress", "pending", "waiting")) {
            try {
                WorkflowRunsResponse response = client.listWorkflowRunsByStatus(
                    owner, repo, status, auth, 100, 1
                );
                if (response.workflowRuns() != null) {
                    allRuns.addAll(response.workflowRuns());
                }
            } catch (HttpClientResponseException e) {
                // Some statuses might not be valid for all repos, ignore errors
                if (e.getStatus().getCode() != 422) {
                    throw e;
                }
            }
        }
        
        return allRuns;
    }

    /**
     * Cancels a single workflow run.
     * @return true if cancelled successfully, false otherwise
     */
    public boolean cancelRun(String owner, String repo, long runId, String token) {
        String auth = "Bearer " + token;
        try {
            client.cancelWorkflowRun(owner, repo, runId, auth);
            return true;
        } catch (HttpClientResponseException e) {
            // 409 Conflict means already completed/cancelled - that's okay
            return e.getStatus().getCode() == 409;
        }
    }

    /**
     * Cancels all cancellable workflow runs, reporting progress via the callback.
     * @return the number of runs successfully cancelled
     */
    public int cancelAllRuns(String owner, String repo, String token, Consumer<String> progressCallback) {
        List<WorkflowRun> runs = getCancellableRuns(owner, repo, token);
        
        if (runs.isEmpty()) {
            progressCallback.accept("No cancellable workflow runs found.");
            return 0;
        }
        
        progressCallback.accept("Found " + runs.size() + " cancellable workflow run(s).");
        
        int cancelled = 0;
        for (WorkflowRun run : runs) {
            progressCallback.accept(String.format(
                "Cancelling run #%d (%s) on branch '%s' [%s]...",
                run.runNumber(), run.name(), run.headBranch(), run.status()
            ));
            
            if (cancelRun(owner, repo, run.id(), token)) {
                cancelled++;
                progressCallback.accept("  ✓ Cancelled successfully");
            } else {
                progressCallback.accept("  ✗ Failed to cancel");
            }
        }
        
        return cancelled;
    }
}
