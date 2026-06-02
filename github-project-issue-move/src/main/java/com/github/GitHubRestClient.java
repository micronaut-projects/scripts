package com.github;

import com.github.models.AddProjectItemResponse;
import com.github.models.Project;
import com.github.models.ProjectItem;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Headers;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;

import java.util.List;
import java.util.Map;

@Client(id = "github")
@Headers({
        @Header(name = HttpHeaders.ACCEPT, value = "application/vnd.github+json"),
        @Header(name = HttpHeaders.USER_AGENT, value = "github-project-issue-move"),
        @Header(name = "X-GitHub-Api-Version", value = "2026-03-10")
})
public interface GitHubRestClient {

    @Get("/orgs/{org}/projectsV2{?after,per_page}")
    HttpResponse<List<Project>> listProjects(@Header(HttpHeaders.AUTHORIZATION) String authorization,
                                             @PathVariable String org,
                                             @Nullable @QueryValue String after,
                                             @QueryValue("per_page") int perPage);

    @Get("/orgs/{org}/projectsV2/{projectNumber}")
    Project getProject(@Header(HttpHeaders.AUTHORIZATION) String authorization,
                       @PathVariable String org,
                       @PathVariable Integer projectNumber);

    @Get("/orgs/{org}/projectsV2/{projectNumber}/items{?after,per_page}")
    HttpResponse<List<ProjectItem>> listProjectItems(@Header(HttpHeaders.AUTHORIZATION) String authorization,
                                                     @PathVariable String org,
                                                     @PathVariable Integer projectNumber,
                                                     @Nullable @QueryValue String after,
                                                     @QueryValue("per_page") int perPage);

    @Get("/repos/{owner}/{repo}/issues/{issueNumber}")
    HttpResponse<?> getIssue(@Header(HttpHeaders.AUTHORIZATION) String authorization,
                                      @PathVariable String owner,
                                      @PathVariable String repo,
                                      @PathVariable Integer issueNumber);

    @Get("/repos/{owner}/{repo}/pulls/{pullNumber}")
    HttpResponse<?> getPullRequest(@Header(HttpHeaders.AUTHORIZATION) String authorization,
                                      @PathVariable String owner,
                                      @PathVariable String repo,
                                      @PathVariable Integer pullNumber);

    @Post("/orgs/{org}/projectsV2/{projectNumber}/items")
    HttpResponse<AddProjectItemResponse> addProjectItem(@Header(HttpHeaders.AUTHORIZATION) String authorization,
                                                        @PathVariable String org,
                                                        @PathVariable Integer projectNumber,
                                                        @Body Map<String, Object> request);

    @Delete("/orgs/{org}/projectsV2/{projectNumber}/items/{itemId}")
    HttpResponse<?> deleteProjectItem(@Header(HttpHeaders.AUTHORIZATION) String authorization,
                                         @PathVariable String org,
                                         @PathVariable Integer projectNumber,
                                         @PathVariable Long itemId);
}
