package io.micronaut.scripts;

public record SyncRequest(String organization, String repository, String token, boolean dryRun) {
}
