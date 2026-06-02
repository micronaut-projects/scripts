package com.github.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Project(Long id,
                      @JsonProperty("node_id") String nodeId,
                      String title,
                      Integer number,
                      Owner owner) {
}
