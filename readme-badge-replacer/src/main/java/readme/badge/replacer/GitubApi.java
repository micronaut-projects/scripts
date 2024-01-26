package readme.badge.replacer;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;

import java.util.List;

import static io.micronaut.http.HttpHeaders.ACCEPT;
import static io.micronaut.http.HttpHeaders.AUTHORIZATION;
import static io.micronaut.http.HttpHeaders.USER_AGENT;

@Client("https://api.github.com")
@Header(name = USER_AGENT, value = "Micronaut HTTP Client")
@Header(name = ACCEPT, value = "application/vnd.github.v3+json, application/json")
@Header(name = AUTHORIZATION, value = "Bearer ${github.token}")
public interface GitubApi {

    @Get("/orgs/micronaut-projects/repos?sort=full_name&per_page=200")
    List<Repository> getRepositories();

    @Post("/repos/micronaut-projects/{repo}/pulls")
    HttpResponse<?> createPr(String repo, @Body PullRequest pullRequest);
}
