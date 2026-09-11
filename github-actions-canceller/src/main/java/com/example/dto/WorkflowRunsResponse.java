package com.example.dto;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@Serdeable
public record WorkflowRunsResponse(
    @JsonProperty("total_count") int totalCount,
    @JsonProperty("workflow_runs") List<WorkflowRun> workflowRuns
) {}
