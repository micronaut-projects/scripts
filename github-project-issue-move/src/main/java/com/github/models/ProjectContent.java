package com.github.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ProjectContent(Long id,
                             @JsonProperty("node_id") String nodeId,
                             Integer number,
                             String title,
                             String state,
                             Boolean merged,
                             @JsonProperty("merged_at") String mergedAt,
                             String url,
                             @JsonProperty("repository_url") String repositoryUrl,
                             @JsonProperty("html_url") String htmlUrl) {
}
