package io.micronaut.scripts.github.project.models;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record MoveSummary(String sourceProjectId,
                   String sourceProjectTitle,
                   String destinationProjectId,
                   String destinationProjectTitle,
                   int scannedItems,
                   int movedItems,
                   int skippedClosedOrMergedItems,
                   int skippedUnsupportedItems,
                   int skippedAddFailures,
                   List<String> addFailureMessages) {
}
