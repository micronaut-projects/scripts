package io.micronaut.scripts;

import java.util.List;

/**
 * @param created label names created in the repository (or that would be created in a dry run)
 * @param skipped label names that already existed in the repository
 */
public record SyncSummary(List<String> created, List<String> skipped) {
}
