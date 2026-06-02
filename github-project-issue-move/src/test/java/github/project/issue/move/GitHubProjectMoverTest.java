package github.project.issue.move;

import com.github.GitHubProjectMover;
import com.github.GitHubRestClient;
import com.github.MoveRequest;
import com.github.models.AddProjectItemResponse;
import com.github.models.Project;
import com.github.models.ProjectContent;
import com.github.models.ProjectItem;
import com.github.models.Owner;
import io.micronaut.scripts.github.project.models.MoveSummary;
import io.micronaut.http.HttpResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubProjectMoverTest {

    @Test
    void skipsClosedIssuesAndMergedPullRequests() {
        RecordingGitHubRestClient client = new RecordingGitHubRestClient();
        client.projectsByNumber.add(project(100L, "PVT_source", 1, "Source"));
        client.projectsByNumber.add(project(200L, "PVT_destination", 2, "Destination"));
        client.projectItemsResponses.add(HttpResponse.ok(List.of(
                item(101L, "Issue", 201L, "open", null),
                item(102L, "Issue", 202L, "closed", null),
                item(103L, "PullRequest", 203L, "closed", false, null),
                item(104L, "PullRequest", 204L, "closed", false, "2026-06-02T08:00:00Z"),
                item(105L, "DraftIssue", 205L, "open", null))));
        client.addProjectItemResponses.add(HttpResponse.created(new AddProjectItemResponse(301L, "PVTI_destination_issue", "Issue", null, null)));
        client.addProjectItemResponses.add(HttpResponse.created(new AddProjectItemResponse(null, null, null, null, null)));
        client.deleteProjectItemResponses.add(HttpResponse.noContent());
        client.deleteProjectItemResponses.add(HttpResponse.noContent());

        GitHubProjectMover mover = new GitHubProjectMover(client);

        MoveSummary summary = mover.move(new MoveRequest(
                "micronaut-projects",
                "1",
                "2",
                "github-token"));

        assertEquals("PVT_source", summary.sourceProjectId());
        assertEquals("PVT_destination", summary.destinationProjectId());
        assertEquals(5, summary.scannedItems());
        assertEquals(2, summary.movedItems());
        assertEquals(2, summary.skippedClosedOrMergedItems());
        assertEquals(1, summary.skippedUnsupportedItems());
        assertEquals(0, summary.skippedAddFailures());
        assertEquals(List.of(
                addRequest("Issue", 201L),
                addRequest("PullRequest", 203L)), client.addRequests);
        assertEquals(List.of(101L, 103L), client.deletedItemIds);
        assertEquals("Bearer github-token", client.authorizationHeaders.getFirst());
    }

    @Test
    void addsIssueUsingContentId() {
        RecordingGitHubRestClient client = new RecordingGitHubRestClient();
        client.projectsByNumber.add(project(100L, "PVT_source", 1, "Source"));
        client.projectsByNumber.add(project(200L, "PVT_destination", 2, "Destination"));
        client.projectItemsResponses.add(HttpResponse.ok(List.of(
                item(101L, "Issue", 201L, "open", null))));
        client.addProjectItemResponses.add(HttpResponse.created(new AddProjectItemResponse(301L, "PVTI_destination_issue", "Issue", null, null)));
        client.deleteProjectItemResponses.add(HttpResponse.noContent());

        GitHubProjectMover mover = new GitHubProjectMover(client);

        mover.move(new MoveRequest(
                "micronaut-projects",
                "1",
                "2",
                "github-token"));

        assertEquals(List.of(
                addRequest("Issue", 201L)), client.addRequests);
        assertEquals(List.of(101L), client.deletedItemIds);
    }

    @Test
    void addsPullRequestUsingContentId() {
        RecordingGitHubRestClient client = new RecordingGitHubRestClient();
        client.projectsByNumber.add(project(100L, "PVT_source", 1, "Source"));
        client.projectsByNumber.add(project(200L, "PVT_destination", 2, "Destination"));
        client.projectItemsResponses.add(HttpResponse.ok(List.of(
                item(101L, "PullRequest", 201L, "open", false))));
        client.addProjectItemResponses.add(HttpResponse.created(new AddProjectItemResponse(301L, "PVTI_destination_pr", "PullRequest", null, null)));
        client.deleteProjectItemResponses.add(HttpResponse.noContent());

        GitHubProjectMover mover = new GitHubProjectMover(client);

        mover.move(new MoveRequest(
                "micronaut-projects",
                "1",
                "2",
                "github-token"));

        assertEquals(List.of(
                addRequest("PullRequest", 201L)), client.addRequests);
        assertEquals(List.of(101L), client.deletedItemIds);
    }

    @Test
    void skipsItemWhenAddFails() {
        RecordingGitHubRestClient client = new RecordingGitHubRestClient();
        client.projectsByNumber.add(project(100L, "PVT_source", 1, "Source"));
        client.projectsByNumber.add(project(200L, "PVT_destination", 2, "Destination"));
        client.projectItemsResponses.add(HttpResponse.ok(List.of(
                item(101L, "PullRequest", 201L, "open", false))));
        client.addProjectItemResponses.add(HttpResponse.notFound());

        GitHubProjectMover mover = new GitHubProjectMover(client);

        MoveSummary summary = mover.move(new MoveRequest(
                "micronaut-projects",
                "1",
                "2",
                "github-token"));

        assertEquals(0, summary.movedItems());
        assertEquals(1, summary.skippedAddFailures());
        assertEquals(List.of(
                addRequest("PullRequest", 201L)), client.addRequests);
        assertEquals(0, client.deletedItemIds.size());
        assertEquals(1, summary.addFailureMessages().size());
        assertTrue(summary.addFailureMessages().getFirst().contains("could not add item 201"));
    }

    @Test
    void skipsIssueWhenAddFails() {
        RecordingGitHubRestClient client = new RecordingGitHubRestClient();
        client.projectsByNumber.add(project(100L, "PVT_source", 1, "Source"));
        client.projectsByNumber.add(project(200L, "PVT_destination", 2, "Destination"));
        client.projectItemsResponses.add(HttpResponse.ok(List.of(
                item(101L, "Issue", 201L, "open", false))));
        client.addProjectItemResponses.add(HttpResponse.notFound());

        GitHubProjectMover mover = new GitHubProjectMover(client);

        MoveSummary summary = mover.move(new MoveRequest(
                "micronaut-projects",
                "1",
                "2",
                "github-token"));

        assertEquals(0, summary.movedItems());
        assertEquals(1, summary.skippedAddFailures());
        assertEquals(0, client.deletedItemIds.size());
        assertEquals(1, summary.addFailureMessages().size());
        assertTrue(summary.addFailureMessages().getFirst().contains("could not add item 201"));
    }

    private static Map<String, Object> addRequest(String type, Long id) {
        return Map.of(
                "type", type,
                "id", id);
    }

    private static Project project(Long id, String nodeId, int number, String title) {
        return new Project(id, nodeId, title, number, new Owner("micronaut-projects"));
    }

    private static ProjectItem item(Long itemId, String contentType, Long contentId, String state, Boolean merged) {
        return item(itemId, contentType, contentId, state, merged, null);
    }

    private static ProjectItem item(Long itemId, String contentType, Long contentId, String state, Boolean merged, String mergedAt) {
        return new ProjectItem(
                itemId,
                "PVTI_" + itemId,
                contentType,
                new ProjectContent(contentId, "CONTENT_" + contentId, 1, "Title", state, merged, mergedAt, "https://api.github.com/repos/micronaut-projects/example/issues/1", "https://api.github.com/repos/micronaut-projects/example", "https://github.com/micronaut-projects/example/issues/1"));
    }

    private static final class RecordingGitHubRestClient implements GitHubRestClient {
        private final Queue<Project> projectsByNumber = new ArrayDeque<>();
        private final Queue<HttpResponse<List<Project>>> listProjectsResponses = new ArrayDeque<>();
        private final Queue<HttpResponse<List<ProjectItem>>> projectItemsResponses = new ArrayDeque<>();
        private final Queue<HttpResponse<AddProjectItemResponse>> addProjectItemResponses = new ArrayDeque<>();
        private final Queue<HttpResponse<Void>> contentResponses = new ArrayDeque<>();
        private final Queue<HttpResponse<Void>> deleteProjectItemResponses = new ArrayDeque<>();
        private final List<Map<String, Object>> addRequests = new ArrayList<>();
        private final List<Long> deletedItemIds = new ArrayList<>();
        private final List<String> authorizationHeaders = new ArrayList<>();

        @Override
        public HttpResponse<List<Project>> listProjects(String authorization, String org, String after, int perPage) {
            authorizationHeaders.add(authorization);
            return listProjectsResponses.remove();
        }

        @Override
        public Project getProject(String authorization, String org, Integer projectNumber) {
            authorizationHeaders.add(authorization);
            return projectsByNumber.remove();
        }

        @Override
        public HttpResponse<List<ProjectItem>> listProjectItems(String authorization, String org, Integer projectNumber, String after, int perPage) {
            authorizationHeaders.add(authorization);
            return projectItemsResponses.remove();
        }

        @Override
        public HttpResponse<Void> getIssue(String authorization, String owner, String repo, Integer issueNumber) {
            authorizationHeaders.add(authorization);
            return contentResponses.remove();
        }

        @Override
        public HttpResponse<Void> getPullRequest(String authorization, String owner, String repo, Integer pullNumber) {
            authorizationHeaders.add(authorization);
            return contentResponses.remove();
        }

        @Override
        public HttpResponse<AddProjectItemResponse> addProjectItem(String authorization, String org, Integer projectNumber, Map<String, Object> request) {
            authorizationHeaders.add(authorization);
            addRequests.add(request);
            return addProjectItemResponses.remove();
        }

        @Override
        public HttpResponse<Void> deleteProjectItem(String authorization, String org, Integer projectNumber, Long itemId) {
            authorizationHeaders.add(authorization);
            deletedItemIds.add(itemId);
            return deleteProjectItemResponses.remove();
        }
    }
}
