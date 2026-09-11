package com.example.client;

import com.example.dto.WorkflowRunsResponse;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;

import static io.micronaut.http.HttpHeaders.ACCEPT;
import static io.micronaut.http.HttpHeaders.AUTHORIZATION;
import static io.micronaut.http.HttpHeaders.USER_AGENT;

@Client("https://api.github.com")
@Header(name = USER_AGENT, value = "Micronaut-GitHub-Actions-Canceller")
@Header(name = ACCEPT, value = "application/vnd.github+json")
@Header(name = "X-GitHub-Api-Version", value = "2022-11-28")
public interface GitHubActionsClient {

    @Get("/repos/{owner}/{repo}/actions/runs")
    WorkflowRunsResponse listWorkflowRuns(
        @PathVariable String owner,
        @PathVariable String repo,
        @Header(AUTHORIZATION) String authorization,
        @QueryValue(defaultValue = "100") int perPage,
        @QueryValue(defaultValue = "1") int page
    );

    @Get("/repos/{owner}/{repo}/actions/runs?status={status}")
    WorkflowRunsResponse listWorkflowRunsByStatus(
        @PathVariable String owner,
        @PathVariable String repo,
        @PathVariable String status,
        @Header(AUTHORIZATION) String authorization,
        @QueryValue(defaultValue = "100") int perPage,
        @QueryValue(defaultValue = "1") int page
    );

    @Post("/repos/{owner}/{repo}/actions/runs/{runId}/cancel")
    void cancelWorkflowRun(
        @PathVariable String owner,
        @PathVariable String repo,
        @PathVariable long runId,
        @Header(AUTHORIZATION) String authorization
    );
}
