package com.github.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AddProjectItemResponse(Long id,
                                     @JsonProperty("node_id") String nodeId,
                                     @JsonProperty("content_type") String contentType,
                                     ProjectContent content,
                                     ProjectItem value) {

    public Long projectItemId() {
        return value == null ? id : value.id();
    }
}
