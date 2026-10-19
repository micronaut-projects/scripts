package io.micronaut.scripts;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * A label as returned by, or sent to, the GitHub REST API.
 */
@Serdeable
public record GitHubLabel(String name, String color, @Nullable String description) {
}
