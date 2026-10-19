package io.micronaut.scripts;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Creates every configured {@link Label} that does not yet exist in a GitHub repository.
 */
@Singleton
public class GitHubLabelsSynchronizer implements LabelsSynchronizer {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubLabelsSynchronizer.class);
    private static final int PAGE_SIZE = 100;

    private final GitHubLabelsClient client;
    private final List<Label> labels;

    public GitHubLabelsSynchronizer(GitHubLabelsClient client, List<Label> labels) {
        this.client = client;
        this.labels = labels;
    }

    @Override
    public SyncSummary sync(SyncRequest request) {
        String authorization = "Bearer " + request.token();
        Set<String> existing = existingLabelNames(authorization, request.organization(), request.repository());
        LOG.debug("Repository {}/{} has {} label(s)", request.organization(), request.repository(), existing.size());

        List<String> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Label label : labels) {
            String name = label.getTitle();
            if (existing.contains(name)) {
                skipped.add(name);
                continue;
            }
            if (!request.dryRun()) {
                client.createLabel(authorization, request.organization(), request.repository(),
                        new GitHubLabel(name, label.getColor(), label.getDescription()));
            }
            created.add(name);
        }
        return new SyncSummary(created, skipped);
    }

    /**
     * GitHub label names are case-insensitive, so the returned set is too.
     */
    private Set<String> existingLabelNames(String authorization, String owner, String repo) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        int page = 1;
        List<GitHubLabel> pageLabels;
        do {
            pageLabels = client.listLabels(authorization, owner, repo, page, PAGE_SIZE);
            for (GitHubLabel label : pageLabels) {
                names.add(label.name());
            }
            page++;
        } while (pageLabels.size() == PAGE_SIZE);
        return names;
    }
}
