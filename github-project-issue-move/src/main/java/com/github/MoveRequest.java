package com.github;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotBlank;

@Introspected
public record MoveRequest(@NotBlank String organization,
                          @NotBlank String sourceProject,
                          @NotBlank String destinationProject,
                          @NotBlank String githubToken) {
}
