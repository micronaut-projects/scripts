package com.github;

import com.github.models.AddProjectItemRequest;
import com.github.models.AddProjectItemResponse;
import com.github.models.Project;
import com.github.models.ProjectContent;
import com.github.models.ProjectItem;
import io.micronaut.scripts.github.project.exceptions.GitHubProjectMoveException;
import io.micronaut.scripts.github.project.models.MoveSummary;
import io.micronaut.scripts.github.project.services.ProjectMover;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Singleton
public class GitHubProjectMover implements ProjectMover {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubProjectMover.class);
    private static final int PAGE_SIZE = 100;
    private static final String CONTENT_TYPE_ISSUE = "Issue";
    private static final String CONTENT_TYPE_PULL_REQUEST = "PullRequest";

    private final GitHubRestClient client;

    public GitHubProjectMover(GitHubRestClient client) {
        this.client = client;
    }

    @Override
    public MoveSummary move(@NotNull @Valid MoveRequest request) {
        String authorization = "Bearer " + request.githubToken();
        LOG.info("Starting project item move for organization '{}' from '{}' to '{}'",
                request.organization(),
                request.sourceProject(),
                request.destinationProject());
        Project sourceProject = resolveProject(authorization, request.organization(), request.sourceProject(), "source");
        Project destinationProject = resolveProject(authorization, request.organization(), request.destinationProject(), "destination");
        LOG.info("Resolved source project '{}' ({}) and destination project '{}' ({})",
                sourceProject.title(),
                projectLabel(sourceProject),
                destinationProject.title(),
                projectLabel(destinationProject));

        if (sourceProject.id() != null && sourceProject.id().equals(destinationProject.id())) {
            throw new GitHubProjectMoveException("Source and destination resolve to the same project.");
        }
        if (sourceProject.number() != null && sourceProject.number().equals(destinationProject.number())) {
            throw new GitHubProjectMoveException("Source and destination resolve to the same project.");
        }

        int scannedItems = 0;
        int movedItems = 0;
        int skippedClosedOrMergedItems = 0;
        int skippedUnsupportedItems = 0;
        int skippedAddFailures = 0;
        List<String> addFailureMessages = new ArrayList<>();
        String cursor = null;
        int page = 0;

        do {
            HttpResponse<List<ProjectItem>> response = client.listProjectItems(
                    authorization,
                    request.organization(),
                    sourceProject.number(),
                    cursor,
                    PAGE_SIZE);
            List<ProjectItem> items = response.getBody().orElse(List.of());
            page++;
            LOG.info("Scanning source project {} page {}: {} item(s)",
                    projectLabel(sourceProject),
                    page,
                    items.size());

            for (ProjectItem item : items) {
                scannedItems++;
                LOG.info("Processing item {} of source project {}: {}",
                        scannedItems,
                        projectLabel(sourceProject),
                        itemLabel(item));

                if (!isPullRequest(item) && !isIssue(item)) {
                    skippedUnsupportedItems++;
                    LOG.info("Skipping unsupported project item: {}", itemLabel(item));
                    continue;
                }

                if (isIssue(item) && isClosedIssue(item)) {
                    skippedClosedOrMergedItems++;
                    LOG.info("Skipping closed issue: {}", itemLabel(item));
                    continue;
                }
                if (isPullRequest(item) && isMergedPullRequest(item)) {
                    skippedClosedOrMergedItems++;
                    LOG.info("Skipping merged pull request: {}", itemLabel(item));
                    continue;
                }

                ProjectContent content = item.content();
                if (content == null || content.id() == null) {
                    throw new GitHubProjectMoveException("Source project item " + item.id() + " is missing its issue or pull request content ID.");
                }

                AddResult addResult = addToDestination(authorization, request.organization(), destinationProject, item, content);
                if (!addResult.added()) {
                    skippedAddFailures++;
                    addFailureMessages.add(addResult.message());
                    LOG.warn("Skipping source removal because the item could not be added to the destination project. {}", addResult.message());
                    continue;
                }

                HttpResponse<?> deleteResponse = client.deleteProjectItem(
                        authorization,
                        request.organization(),
                        sourceProject.number(),
                        item.id());
                if (deleteResponse.code() != 204) {
                    throw new GitHubProjectMoveException("Could not remove item from source project: GitHub returned HTTP " + deleteResponse.code() + ".");
                }

                movedItems++;
                LOG.info("Moved item to destination project {} and removed it from source project {}: {}",
                        projectLabel(destinationProject),
                        projectLabel(sourceProject),
                        itemLabel(item));
            }

            cursor = nextAfterCursor(response);
        } while (cursor != null);

        LOG.info("Finished project item move: scanned={}, moved={}, skippedClosedOrMerged={}, skippedUnsupported={}, skippedAddFailures={}",
                scannedItems,
                movedItems,
                skippedClosedOrMergedItems,
                skippedUnsupportedItems,
                skippedAddFailures);
        return new MoveSummary(
                projectIdentifier(sourceProject),
                sourceProject.title(),
                projectIdentifier(destinationProject),
                destinationProject.title(),
                scannedItems,
                movedItems,
                skippedClosedOrMergedItems,
                skippedUnsupportedItems,
                skippedAddFailures,
                List.copyOf(addFailureMessages));
    }

    private Project resolveProject(String authorization, String organization, String projectReference, String label) {
        ProjectReference reference = ProjectReference.parse(projectReference);
        if (reference.isNumber()) {
            Project project = client.getProject(authorization, organization, reference.number());
            validateResolvedProject(project, organization, projectReference, label);
            return project;
        }

        String cursor = null;
        do {
            HttpResponse<List<Project>> response = client.listProjects(authorization, organization, cursor, PAGE_SIZE);
            for (Project project : response.getBody().orElse(List.of())) {
                if (reference.matches(project)) {
                    validateResolvedProject(project, organization, projectReference, label);
                    return project;
                }
            }
            cursor = nextAfterCursor(response);
        } while (cursor != null);

        throw new GitHubProjectMoveException("Could not resolve " + label + " project '" + projectReference + "'.");
    }

    private void validateResolvedProject(Project project, String organization, String projectReference, String label) {
        if (project == null || project.number() == null) {
            throw new GitHubProjectMoveException("Could not resolve " + label + " project '" + projectReference + "'.");
        }
        if (project.owner() == null || project.owner().login() == null || !project.owner().login().equalsIgnoreCase(organization)) {
            String ownerLogin = project.owner() == null ? "unknown" : project.owner().login();
            throw new GitHubProjectMoveException("The " + label + " project belongs to '" + ownerLogin + "', not organization '" + organization + "'.");
        }
    }

    private AddResult addToDestination(String authorization,
                                       String organization,
                                       Project destinationProject,
                                       ProjectItem item,
                                       ProjectContent content) {
        LOG.debug("Adding item {} {} to destination project {}", item.content().id(), item.content().title(), projectLabel(destinationProject));
        HttpResponse<AddProjectItemResponse> addResponse = client.addProjectItem(authorization, organization, destinationProject.number(),
                Map.of(
                "type",item.contentType(),
                "id", item.content().id()
                ));
        if (isSuccessful(addResponse)) {
            LOG.debug("Adding item {} {} to destination project {}", item.content().id(), item.content().title(), projectLabel(destinationProject));
            return AddResult.success();
        }
        return AddResult.skipped("could not add item " + item.content().id() + " " + item.content().title() + " to destination project " + projectLabel(destinationProject));

    }

    private AddProjectItemRequest addRequest(ProjectItem item, ProjectContent content) {
        RepositoryReference repository = repositoryReference(content);
        if (repository != null && content.number() != null) {
            return AddProjectItemRequest.byRepository(item.contentType(), repository.owner(), repository.name(), content.number());
        }
        return AddProjectItemRequest.byId(item.contentType(), content.id());
    }

    private AddProjectItemRequest pullRequestAsIssueRequest(ProjectItem item, ProjectContent content) {
        if (!CONTENT_TYPE_PULL_REQUEST.equals(item.contentType())) {
            return null;
        }
        RepositoryReference repository = repositoryReference(content);
        if (repository == null || content.number() == null) {
            return null;
        }
        return AddProjectItemRequest.byRepository(CONTENT_TYPE_ISSUE, repository.owner(), repository.name(), content.number());
    }

    private boolean isSuccessful(HttpResponse<?> response) {
        return response != null && response.code() >= 200 && response.code() < 300;
    }

    private boolean destinationContainsItem(String authorization,
                                            String organization,
                                            Project destinationProject,
                                            ProjectItem sourceItem,
                                            ProjectContent sourceContent) {
        String cursor = null;
        do {
            HttpResponse<List<ProjectItem>> response = client.listProjectItems(
                    authorization,
                    organization,
                    destinationProject.number(),
                    cursor,
                    PAGE_SIZE);
            for (ProjectItem destinationItem : response.getBody().orElse(List.of())) {
                if (sameContent(sourceItem, sourceContent, destinationItem)) {
                    return true;
                }
            }
            cursor = nextAfterCursor(response);
        } while (cursor != null);
        return false;
    }

    private boolean sameContent(ProjectItem sourceItem, ProjectContent sourceContent, ProjectItem destinationItem) {
        if (destinationItem == null || destinationItem.content() == null || !sourceItem.contentType().equals(destinationItem.contentType())) {
            return false;
        }

        ProjectContent destinationContent = destinationItem.content();
        if (sourceContent.id() != null && sourceContent.id().equals(destinationContent.id())) {
            return true;
        }
        if (sourceContent.nodeId() != null && sourceContent.nodeId().equals(destinationContent.nodeId())) {
            return true;
        }
        if (sourceContent.htmlUrl() != null && sourceContent.htmlUrl().equals(destinationContent.htmlUrl())) {
            return true;
        }

        RepositoryReference sourceRepository = repositoryReference(sourceContent);
        RepositoryReference destinationRepository = repositoryReference(destinationContent);
        return sourceRepository != null
                && sourceRepository.equals(destinationRepository)
                && sourceContent.number() != null
                && sourceContent.number().equals(destinationContent.number());
    }

    private String addFailureMessage(String authorization,
                                     Project destinationProject,
                                     ProjectItem item,
                                     ProjectContent content,
                                     AddProjectItemRequest firstRequest,
                                     HttpResponse<?> firstResponse,
                                     AddProjectItemRequest fallbackRequest,
                                     HttpResponse<?> fallbackResponse,
                                     AddProjectItemRequest pullRequestAsIssueRequest,
                                     HttpResponse<?> pullRequestAsIssueResponse) {
        String contentAccess = contentAccessMessage(authorization, item, content);
        return "Could not add " + item.contentType()
                + " to destination project " + projectIdentifier(destinationProject)
                + " (number " + destinationProject.number() + ")."
                + " Source project item id=" + item.id()
                + ", content id=" + content.id()
                + ", content number=" + content.number()
                + ", content url=" + content.htmlUrl()
                + ", first add request=" + describeAddRequest(firstRequest)
                + " returned HTTP " + statusCode(firstResponse)
                + ", fallback add request=" + describeAddRequest(fallbackRequest)
                + " returned HTTP " + statusCode(fallbackResponse)
                + ", pull request as issue add request=" + describeAddRequest(pullRequestAsIssueRequest)
                + " returned HTTP " + statusCode(pullRequestAsIssueResponse)
                + ", and the item was not found in the destination project after those attempts."
                + " Authenticated content lookup: " + contentAccess + ".";
    }

    private String contentAccessMessage(String authorization, ProjectItem item, ProjectContent content) {
        RepositoryReference repository = repositoryReference(content);
        if (repository == null || content.number() == null) {
            return "not checked because repository or number could not be resolved from the project item";
        }
        HttpResponse<?> response = CONTENT_TYPE_PULL_REQUEST.equals(item.contentType())
                ? client.getPullRequest(authorization, repository.owner(), repository.name(), content.number())
                : client.getIssue(authorization, repository.owner(), repository.name(), content.number());
        return repository.owner() + "/" + repository.name() + "#" + content.number()
                + " returned HTTP " + statusCode(response);
    }

    private int statusCode(HttpResponse<?> response) {
        return response == null ? 0 : response.code();
    }

    private String describeAddRequest(AddProjectItemRequest request) {
        if (request == null) {
            return "null";
        }
        if (request.owner() != null && request.repo() != null && request.number() != null) {
            return request.type() + " " + request.owner() + "/" + request.repo() + "#" + request.number();
        }
        return request.type() + " id=" + request.id();
    }

    private RepositoryReference repositoryReference(ProjectContent content) {
        RepositoryReference fromRepositoryUrl = repositoryReferenceFromRepositoryUrl(content.repositoryUrl());
        if (fromRepositoryUrl != null) {
            return fromRepositoryUrl;
        }
        return repositoryReferenceFromHtmlUrl(content.htmlUrl());
    }

    private RepositoryReference repositoryReferenceFromRepositoryUrl(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return null;
        }
        URI uri = URI.create(repositoryUrl);
        String[] segments = uri.getPath().split("/");
        if (segments.length >= 4 && "repos".equals(segments[1])) {
            return new RepositoryReference(segments[2], segments[3]);
        }
        return null;
    }

    private RepositoryReference repositoryReferenceFromHtmlUrl(String htmlUrl) {
        if (htmlUrl == null || htmlUrl.isBlank()) {
            return null;
        }
        URI uri = URI.create(htmlUrl);
        String[] segments = uri.getPath().split("/");
        if (segments.length >= 3) {
            return new RepositoryReference(segments[1], segments[2]);
        }
        return null;
    }

    private boolean isPullRequest(ProjectItem item) {
        return item != null
                && item.id() != null
                && item.contentType() != null
                && CONTENT_TYPE_PULL_REQUEST.equals(item.contentType());
    }

    private boolean isIssue(ProjectItem item) {
        return item != null
                && item.id() != null
                && item.contentType() != null
                && (CONTENT_TYPE_ISSUE.equals(item.contentType()));
    }

    private boolean isClosedIssue(ProjectItem item) {
        ProjectContent content = item.content();
        if (content == null) {
            return false;
        }
        if (CONTENT_TYPE_ISSUE.equals(item.contentType())) {
            return "closed".equalsIgnoreCase(content.state());
        }
        return false;
    }

    private boolean isMergedPullRequest(ProjectItem item) {
        ProjectContent content = item.content();
        if (content == null) {
            return false;
        }
        return CONTENT_TYPE_PULL_REQUEST.equals(item.contentType())
                && (Boolean.TRUE.equals(content.merged()) || content.mergedAt() != null && !content.mergedAt().isBlank());
    }

    private String projectIdentifier(Project project) {
        if (project.nodeId() != null && !project.nodeId().isBlank()) {
            return project.nodeId();
        }
        return String.valueOf(project.number());
    }

    private String projectLabel(Project project) {
        if (project == null) {
            return "unknown";
        }
        String identifier = projectIdentifier(project);
        if (project.number() == null) {
            return identifier;
        }
        return identifier + " (number " + project.number() + ")";
    }

    private String itemLabel(ProjectItem item) {
        if (item == null) {
            return "unknown item";
        }

        ProjectContent content = item.content();
        if (content == null) {
            return item.contentType() + " item-id=" + item.id() + " without content";
        }

        return item.contentType()
                + " " + contentReference(content)
                + " item-id=" + item.id()
                + " content-id=" + content.id()
                + " state=" + content.state()
                + " merged=" + pullRequestMergedLabel(item, content)
                + " title=" + quoted(content.title());
    }

    private String contentReference(ProjectContent content) {
        RepositoryReference repository = repositoryReference(content);
        if (repository != null && content.number() != null) {
            return repository.owner() + "/" + repository.name() + "#" + content.number();
        }
        if (content.htmlUrl() != null && !content.htmlUrl().isBlank()) {
            return content.htmlUrl();
        }
        if (content.url() != null && !content.url().isBlank()) {
            return content.url();
        }
        return "#" + content.number();
    }

    private String pullRequestMergedLabel(ProjectItem item, ProjectContent content) {
        if (!CONTENT_TYPE_PULL_REQUEST.equals(item.contentType())) {
            return "n/a";
        }
        return String.valueOf(Boolean.TRUE.equals(content.merged()) || content.mergedAt() != null && !content.mergedAt().isBlank());
    }

    private String quoted(String value) {
        if (value == null || value.isBlank()) {
            return "\"\"";
        }
        return "\"" + value.replace('\n', ' ').replace('\r', ' ') + "\"";
    }

    private String nextAfterCursor(HttpResponse<?> response) {
        String link = response.getHeaders().get(HttpHeaders.LINK);
        if (link == null || link.isBlank()) {
            return null;
        }
        for (String segment : link.split(",")) {
            if (!segment.contains("rel=\"next\"")) {
                continue;
            }
            int start = segment.indexOf('<');
            int end = segment.indexOf('>');
            if (start < 0 || end <= start) {
                continue;
            }
            String uri = segment.substring(start + 1, end);
            return queryParameter(uri, "after");
        }
        return null;
    }

    private String queryParameter(String uri, String name) {
        String query = URI.create(uri).getQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            String key = separator < 0 ? pair : pair.substring(0, separator);
            if (name.equals(key)) {
                return separator < 0 ? "" : pair.substring(separator + 1);
            }
        }
        return null;
    }

    private <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
    }

    private record ProjectReference(String value, Integer number) {

        static ProjectReference parse(String value) {
            if (value == null || value.isBlank()) {
                throw new GitHubProjectMoveException("Project reference cannot be blank.");
            }
            String trimmed = value.trim();
            if (trimmed.chars().allMatch(Character::isDigit)) {
                try {
                    int number = Integer.parseInt(trimmed);
                    if (number < 1) {
                        throw new GitHubProjectMoveException("Project number must be greater than zero.");
                    }
                    return new ProjectReference(trimmed, number);
                } catch (NumberFormatException e) {
                    throw new GitHubProjectMoveException("Project number is too large: " + trimmed, e);
                }
            }
            return new ProjectReference(trimmed.toLowerCase(Locale.ROOT), null);
        }

        boolean isNumber() {
            return number != null;
        }

        boolean matches(Project project) {
            if (project == null) {
                return false;
            }
            return equalsIgnoreCase(value, project.nodeId()) || equalsIgnoreCase(value, String.valueOf(project.id()));
        }

        private boolean equalsIgnoreCase(String left, String right) {
            return left != null && right != null && left.equalsIgnoreCase(right);
        }
    }

    private record RepositoryReference(String owner, String name) {
    }

    private record AddResult(boolean added, String message) {

        static AddResult success() {
            return new AddResult(true, null);
        }

        static AddResult skipped(String message) {
            return new AddResult(false, message);
        }
    }
}
