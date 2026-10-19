package io.micronaut.scripts;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Headers;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;

import java.util.List;

/**
 * Declarative client for the GitHub labels REST API.
 * @see <a href="https://docs.github.com/en/rest/issues/labels">GitHub REST API: Labels</a>
 */
@Client(id = "github")
@Headers({
        @Header(name = HttpHeaders.ACCEPT, value = "application/vnd.github+json"),
        @Header(name = HttpHeaders.USER_AGENT, value = "github-labels-sync"),
        @Header(name = "X-GitHub-Api-Version", value = "2022-11-28")
})
public interface GitHubLabelsClient {

    @Get("/repos/{owner}/{repo}/labels{?page,per_page}")
    List<GitHubLabel> listLabels(@Header(HttpHeaders.AUTHORIZATION) String authorization,
                                 @PathVariable String owner,
                                 @PathVariable String repo,
                                 @QueryValue int page,
                                 @QueryValue("per_page") int perPage);

    @Post("/repos/{owner}/{repo}/labels")
    GitHubLabel createLabel(@Header(HttpHeaders.AUTHORIZATION) String authorization,
                            @PathVariable String owner,
                            @PathVariable String repo,
                            @Body GitHubLabel label);
}
