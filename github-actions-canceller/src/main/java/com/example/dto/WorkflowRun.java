package com.example.dto;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

@Serdeable
public record WorkflowRun(
    long id,
    String name,
    @JsonProperty("head_branch") String headBranch,
    @JsonProperty("head_sha") String headSha,
    String status,
    String conclusion,
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("run_number") int runNumber,
    @JsonProperty("workflow_id") long workflowId
) {
    public boolean isCancellable() {
        return "queued".equals(status) || 
               "in_progress".equals(status) || 
               "pending".equals(status) ||
               "waiting".equals(status);
    }
}
