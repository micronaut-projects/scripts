package io.micronaut.scripts.minorbranch;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Prototype
@Command(name = "minor-branch-creator", description = "Create the next Micronaut minor branch for a GitHub repository.",
        mixinStandardHelpOptions = true)
public class MinorBranchCreatorCommand implements Runnable {

    private static final String DEFAULT_GITHUB_OWNER = "micronaut-projects";

    private final MinorBranchCreator minorBranchCreator;
    private final GitHubOrgConfiguration gitHubOrgConfiguration;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Option(names = {"-u", "--url"}, required = true, paramLabel = "URL",
            description = "URL of the libs.versions.toml file.")
    URI url;

    @Option(names = {"-r", "--repository"}, paramLabel = "OWNER/REPO",
            description = "GitHub repository where the branch will be created. Defaults to configured githuborg.repos.")
    String repository;

    @Option(names = "--set-default", description = "Set the new minor branch as the repository default branch.")
    boolean setDefault;

    @Option(names = "--dry-run", description = "Show the GitHub actions that would be executed without creating or updating anything.")
    boolean dryRun;

    @Option(names = "--github-token", paramLabel = "TOKEN",
            description = "GitHub token. Defaults to GITHUB_TOKEN, then GH_TOKEN.")
    String githubToken;

    @Inject
    public MinorBranchCreatorCommand(MinorBranchCreator minorBranchCreator, GitHubOrgConfiguration gitHubOrgConfiguration) {
        this.minorBranchCreator = minorBranchCreator;
        this.gitHubOrgConfiguration = gitHubOrgConfiguration;
    }

    public static void main(String[] args) throws Exception {
        PicocliRunner.run(MinorBranchCreatorCommand.class, args);
    }

    @Override
    public void run() {
        List<GitHubRepository> repositories = resolveRepositories();
        String token = resolveGitHubToken();
        int failures = 0;
        if (repositories.size() > 1) {
            spec.commandLine().getOut().printf("Repositories: %d%n%n", repositories.size());
        }
        for (int i = 0; i < repositories.size(); i++) {
            GitHubRepository parsedRepository = repositories.get(i);
            if (i > 0) {
                spec.commandLine().getOut().println();
            }
            try {
                MinorBranchResult result = minorBranchCreator.create(url, parsedRepository, setDefault, dryRun, token);
                printResult(result);
            } catch (MinorBranchCreatorException e) {
                failures++;
                spec.commandLine().getErr().printf("Repository %s failed: %s%n", parsedRepository, e.getMessage());
            }
        }
        if (failures > 0) {
            throw new CommandLine.ExecutionException(spec.commandLine(), failures + " repositories failed.");
        }
    }

    private List<GitHubRepository> resolveRepositories() {
        if (repository != null && !repository.isBlank()) {
            return List.of(parseRepository(repository));
        }

        List<String> configuredRepos = gitHubOrgConfiguration.getRepos();
        if (configuredRepos.isEmpty()) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "Either pass --repository or configure githuborg.repos.");
        }
        List<GitHubRepository> repositories = new ArrayList<>(configuredRepos.size());
        for (String configuredRepo : configuredRepos) {
            repositories.add(parseConfiguredRepository(configuredRepo));
        }
        return repositories;
    }

    private GitHubRepository parseConfiguredRepository(String value) {
        try {
            if (value != null && !value.isBlank() && !value.contains("/") && !value.startsWith("https://github.com/")) {
                return new GitHubRepository(DEFAULT_GITHUB_OWNER, value.trim());
            }
            return GitHubRepository.parse(value);
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(spec.commandLine(), e.getMessage());
        }
    }

    private GitHubRepository parseRepository(String value) {
        try {
            return GitHubRepository.parse(value);
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(spec.commandLine(), e.getMessage());
        }
    }

    private String resolveGitHubToken() {
        String token = firstNonBlank(githubToken, System.getenv("GITHUB_TOKEN"), System.getenv("GH_TOKEN"));
        if (token == null) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "A GitHub token is required. Pass --github-token or set GITHUB_TOKEN/GH_TOKEN.");
        }
        return token;
    }

    private void printResult(MinorBranchResult result) {
        PrintWriter out = spec.commandLine().getOut();
        out.printf("Repository: %s%n", result.repository());
        out.printf("Current default branch: %s%n", result.sourceDefaultBranch());
        out.printf("Resolved platform version: %s%n", result.platformVersion());
        out.printf("Target minor branch: %s%n", result.targetBranch());
        if (result.dryRun()) {
            out.println("Mode: dry run");
            out.printf("Branch: %s%n", result.branchCreationPlanned() ? "would be created" : "already exists");
            out.printf("Default branch: %s%n", result.defaultBranchUpdatePlanned() ? "would be updated" : "unchanged");
            out.printf("Create branch action: %s%n", result.branchCreationPlanned()
                    ? "refs/heads/" + result.targetBranch() + " from " + result.sourceDefaultBranch()
                    : "none");
            out.printf("Set default branch action: %s%n", result.defaultBranchUpdatePlanned() ? result.targetBranch() : "none");
        } else {
            out.printf("Branch: %s%n", result.branchCreated() ? "created" : "already existed");
            if (setDefault) {
                out.printf("Default branch: %s%n", result.defaultBranchUpdated() ? "updated" : "already set");
            } else {
                out.println("Default branch: unchanged");
            }
        }
        out.flush();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}

@Singleton
class MinorBranchCreator {

    private final GitHubOperations gitHubOperations;

    @Inject
    MinorBranchCreator(GitHubOperations gitHubOperations) {
        this.gitHubOperations = gitHubOperations;
    }

    MinorBranchResult create(URI url, GitHubRepository repository, boolean setDefault, boolean dryRun, String token) {
        GitHubRepositoryState repositoryState = gitHubOperations.repositoryState(repository, token);
        String toml = gitHubOperations.fetchText(url, token);
        String version = VersionsToml.findVersion(toml, repository.name())
                .orElseThrow(() -> new MinorBranchCreatorException(
                        "Could not find a version entry for " + repository.name() + " in " + url));
        String targetBranch = MinorBranchName.nextMinorBranch(version);
        BranchRef sourceRef = gitHubOperations.branchRef(repository, repositoryState.defaultBranch(), token);

        boolean branchExists = gitHubOperations.branchExists(repository, targetBranch, token);
        boolean branchCreationPlanned = !branchExists;
        boolean branchCreated = false;
        if (branchCreationPlanned && !dryRun) {
            gitHubOperations.createBranch(repository, targetBranch, sourceRef.sha(), token);
            branchCreated = true;
        }

        boolean defaultBranchUpdatePlanned = setDefault && !targetBranch.equals(repositoryState.defaultBranch());
        boolean defaultBranchUpdated = false;
        if (defaultBranchUpdatePlanned && !dryRun) {
            gitHubOperations.updateDefaultBranch(repository, targetBranch, token);
            defaultBranchUpdated = true;
        }
        return new MinorBranchResult(repository.toString(), repositoryState.defaultBranch(), version, targetBranch,
                dryRun, branchCreationPlanned, branchCreated, defaultBranchUpdatePlanned, defaultBranchUpdated);
    }
}

interface GitHubOperations {

    GitHubRepositoryState repositoryState(GitHubRepository repository, String token);

    String fetchText(URI url, String token);

    BranchRef branchRef(GitHubRepository repository, String branch, String token);

    boolean branchExists(GitHubRepository repository, String branch, String token);

    void createBranch(GitHubRepository repository, String branch, String sha, String token);

    void updateDefaultBranch(GitHubRepository repository, String branch, String token);
}

@Singleton
class GitHubApiClient implements GitHubOperations {

    private static final URI GITHUB_API_URI = URI.create("https://api.github.com");
    private static final String GITHUB_API_VERSION = "2022-11-28";
    private static final String USER_AGENT = "minor-branch-creator";

    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final URI githubApiUri;

    @Inject
    GitHubApiClient(JsonMapper jsonMapper) {
        this(HttpClient.newHttpClient(), jsonMapper, GITHUB_API_URI);
    }

    GitHubApiClient(HttpClient httpClient, JsonMapper jsonMapper, URI githubApiUri) {
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        this.githubApiUri = githubApiUri;
    }

    @Override
    public GitHubRepositoryState repositoryState(GitHubRepository repository, String token) {
        Map<String, Object> body = requestJson("load repository metadata",
                requestBuilder(repositoryUri(repository), token).GET().build(),
                200);
        return new GitHubRepositoryState(requiredString(body, "default_branch", "repository default branch"));
    }

    @Override
    public String fetchText(URI url, String token) {
        Optional<GitHubFile> gitHubFile = GitHubFile.fromBlobUrl(url);
        if (gitHubFile.isPresent()) {
            return fetchGitHubFile(gitHubFile.get(), token);
        }

        HttpRequest request = HttpRequest.newBuilder(url)
                .header("Accept", "text/plain")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> response = send("download TOML file", request);
        if (response.statusCode() != 200) {
            throw apiException("download TOML file", response);
        }
        return response.body();
    }

    @Override
    public BranchRef branchRef(GitHubRepository repository, String branch, String token) {
        Map<String, Object> body = requestJson("load default branch ref",
                requestBuilder(gitRefUri(repository, branch), token).GET().build(),
                200);
        Object object = body.get("object");
        if (!(object instanceof Map<?, ?> objectBody)) {
            throw new MinorBranchCreatorException("GitHub ref response did not contain an object.");
        }
        Object sha = objectBody.get("sha");
        if (!(sha instanceof String value) || value.isBlank()) {
            throw new MinorBranchCreatorException("GitHub ref response did not contain a SHA.");
        }
        return new BranchRef(value);
    }

    @Override
    public boolean branchExists(GitHubRepository repository, String branch, String token) {
        HttpResponse<String> response = send("check branch existence",
                requestBuilder(gitRefUri(repository, branch), token).GET().build());
        if (response.statusCode() == 200) {
            return true;
        }
        if (response.statusCode() == 404) {
            return false;
        }
        throw apiException("check branch existence", response);
    }

    @Override
    public void createBranch(GitHubRepository repository, String branch, String sha, String token) {
        String body = writeJson(Map.of(
                "ref", "refs/heads/" + branch,
                "sha", sha
        ));
        HttpRequest request = requestBuilder(gitRefsUri(repository), token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = send("create branch", request);
        if (response.statusCode() != 201) {
            throw apiException("create branch", response);
        }
    }

    @Override
    public void updateDefaultBranch(GitHubRepository repository, String branch, String token) {
        String body = writeJson(Map.of("default_branch", branch));
        HttpRequest request = requestBuilder(repositoryUri(repository), token)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = send("update default branch", request);
        if (response.statusCode() != 200) {
            throw apiException("update default branch", response);
        }
    }

    private String fetchGitHubFile(GitHubFile file, String token) {
        Map<String, Object> body = requestJson("load TOML file from GitHub",
                requestBuilder(contentsUri(file), token).GET().build(),
                200);
        String content = requiredString(body, "content", "GitHub file content");
        String encoding = requiredString(body, "encoding", "GitHub file encoding");
        if (!"base64".equalsIgnoreCase(encoding)) {
            throw new MinorBranchCreatorException("Unsupported GitHub content encoding: " + encoding);
        }
        return new String(Base64.getMimeDecoder().decode(content), StandardCharsets.UTF_8);
    }

    private Map<String, Object> requestJson(String action, HttpRequest request, int expectedStatusCode) {
        HttpResponse<String> response = send(action, request);
        if (response.statusCode() != expectedStatusCode) {
            throw apiException(action, response);
        }
        return readJsonMap(response.body());
    }

    private HttpRequest.Builder requestBuilder(URI uri, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .header("User-Agent", USER_AGENT);
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private HttpResponse<String> send(String action, HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MinorBranchCreatorException("Could not " + action + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MinorBranchCreatorException("Interrupted while trying to " + action + ".", e);
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = jsonMapper.readValue(json, Map.class);
            return body;
        } catch (IOException e) {
            throw new MinorBranchCreatorException("Could not parse GitHub JSON response.", e);
        }
    }

    private String writeJson(Map<String, String> body) {
        try {
            return jsonMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new MinorBranchCreatorException("Could not serialize GitHub API request.", e);
        }
    }

    private MinorBranchCreatorException apiException(String action, HttpResponse<String> response) {
        String message = extractGitHubMessage(response.body())
                .map(value -> ": " + value)
                .orElse("");
        return new MinorBranchCreatorException("Could not " + action + " (HTTP " + response.statusCode() + ")" + message);
    }

    private Optional<String> extractGitHubMessage(String body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> error = jsonMapper.readValue(body, Map.class);
            Object message = error.get("message");
            if (message instanceof String value && !value.isBlank()) {
                return Optional.of(value);
            }
        } catch (IOException ignored) {
            // A non-JSON response still reports the status code above.
        }
        return Optional.empty();
    }

    private URI repositoryUri(GitHubRepository repository) {
        return githubApiUri.resolve("/repos/" + encodePathSegment(repository.owner()) + "/" + encodePathSegment(repository.name()));
    }

    private URI gitRefsUri(GitHubRepository repository) {
        return githubApiUri.resolve("/repos/" + encodePathSegment(repository.owner()) + "/" + encodePathSegment(repository.name()) + "/git/refs");
    }

    private URI gitRefUri(GitHubRepository repository, String branch) {
        return githubApiUri.resolve("/repos/" + encodePathSegment(repository.owner()) + "/" + encodePathSegment(repository.name())
                + "/git/ref/heads/" + encodePath(branch));
    }

    private URI contentsUri(GitHubFile file) {
        return githubApiUri.resolve("/repos/" + encodePathSegment(file.repository().owner()) + "/"
                + encodePathSegment(file.repository().name()) + "/contents/" + encodePath(file.path())
                + "?ref=" + encodeQueryValue(file.ref()));
    }

    private static String requiredString(Map<String, Object> body, String key, String description) {
        Object value = body.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new MinorBranchCreatorException("GitHub response did not contain " + description + ".");
    }

    private static String encodePath(String value) {
        return Arrays.stream(value.split("/", -1))
                .map(GitHubApiClient::encodePathSegment)
                .collect(Collectors.joining("/"));
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

record GitHubRepository(String owner, String name) {

    private static final Pattern REPOSITORY_PART = Pattern.compile("[A-Za-z0-9_.-]+");

    GitHubRepository {
        owner = requireRepositoryPart(owner, "owner");
        name = requireRepositoryPart(stripGitSuffix(name), "repository");
    }

    static GitHubRepository parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Repository must be provided as OWNER/REPO.");
        }
        String normalized = value.trim();
        if (normalized.startsWith("https://github.com/")) {
            URI uri = URI.create(normalized);
            List<String> parts = pathParts(uri.getPath());
            if (parts.size() >= 2) {
                return new GitHubRepository(parts.get(0), parts.get(1));
            }
        }
        String[] parts = normalized.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Repository must be provided as OWNER/REPO.");
        }
        return new GitHubRepository(parts[0], parts[1]);
    }

    @Override
    public String toString() {
        return owner + "/" + name;
    }

    private static String requireRepositoryPart(String value, String label) {
        if (value == null || value.isBlank() || !REPOSITORY_PART.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid GitHub repository " + label + ": " + value);
        }
        return value;
    }

    private static String stripGitSuffix(String value) {
        return value != null && value.endsWith(".git") ? value.substring(0, value.length() - 4) : value;
    }

    private static List<String> pathParts(String path) {
        return Arrays.stream(path.split("/"))
                .filter(part -> !part.isBlank())
                .toList();
    }
}

record GitHubRepositoryState(String defaultBranch) {
}

record BranchRef(String sha) {
}

record MinorBranchResult(String repository,
                         String sourceDefaultBranch,
                         String platformVersion,
                         String targetBranch,
                         boolean dryRun,
                         boolean branchCreationPlanned,
                         boolean branchCreated,
                         boolean defaultBranchUpdatePlanned,
                         boolean defaultBranchUpdated) {
}

record GitHubFile(GitHubRepository repository, String ref, String path) {

    static Optional<GitHubFile> fromBlobUrl(URI uri) {
        if (uri == null || uri.getHost() == null || !"github.com".equalsIgnoreCase(uri.getHost())) {
            return Optional.empty();
        }
        List<String> parts = Arrays.stream(uri.getPath().split("/"))
                .filter(part -> !part.isBlank())
                .toList();
        if (parts.size() < 5 || !"blob".equals(parts.get(2))) {
            return Optional.empty();
        }
        String filePath = String.join("/", parts.subList(4, parts.size()));
        return Optional.of(new GitHubFile(new GitHubRepository(parts.get(0), parts.get(1)), parts.get(3), filePath));
    }
}

final class VersionsToml {

    private static final Pattern SECTION = Pattern.compile("^\\[([^]]+)]$");
    private static final Pattern STRING_ASSIGNMENT = Pattern.compile("^(?:\"([^\"]+)\"|([A-Za-z0-9_.-]+))\\s*=\\s*\"([^\"]+)\"\\s*$");
    private static final Map<String, String> VERSION_KEY_ALIASES = Map.of(
            "micronaut-problem-json", "managed-micronaut-problem",
            "micronaut-mongodb", "managed-micronaut-mongo",
            "micronaut-oracle-cloud", "managed-micronaut-oraclecloud",
            "micronaut-discovery-client", "managed-micronaut-discovery"
    );

    private VersionsToml() {
    }

    static Optional<String> findVersion(String toml, String repositoryName) {
        Map<String, String> versions = parseVersions(toml);
        String exactVersion = versions.get(repositoryName);
        if (exactVersion != null) {
            return Optional.of(exactVersion);
        }

        String managedVersion = versions.get("managed-" + repositoryName);
        if (managedVersion != null) {
            return Optional.of(managedVersion);
        }

        String alias = VERSION_KEY_ALIASES.get(repositoryName);
        if (alias != null && versions.containsKey(alias)) {
            return Optional.of(versions.get(alias));
        }

        List<String> suffixMatches = versions.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith("-" + repositoryName))
                .map(Map.Entry::getValue)
                .toList();
        return suffixMatches.size() == 1 ? Optional.of(suffixMatches.get(0)) : Optional.empty();
    }

    private static Map<String, String> parseVersions(String toml) {
        Map<String, String> versions = new LinkedHashMap<>();
        String section = "";
        for (String line : toml.lines().toList()) {
            String stripped = stripTomlComment(line).trim();
            if (stripped.isBlank()) {
                continue;
            }
            Matcher sectionMatcher = SECTION.matcher(stripped);
            if (sectionMatcher.matches()) {
                section = sectionMatcher.group(1).trim().toLowerCase(Locale.ROOT);
                continue;
            }
            if (!"versions".equals(section)) {
                continue;
            }
            Matcher assignment = STRING_ASSIGNMENT.matcher(stripped);
            if (assignment.matches()) {
                String key = assignment.group(1) == null ? assignment.group(2) : assignment.group(1);
                versions.put(key, assignment.group(3));
            }
        }
        return versions;
    }

    private static String stripTomlComment(String line) {
        boolean inString = false;
        boolean escaping = false;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '"' && !escaping) {
                inString = !inString;
            }
            if (character == '#' && !inString) {
                break;
            }
            builder.append(character);
            escaping = character == '\\' && !escaping;
            if (character != '\\') {
                escaping = false;
            }
        }
        return builder.toString();
    }
}

final class MinorBranchName {

    private static final Pattern SEMANTIC_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$");

    private MinorBranchName() {
    }

    static String nextMinorBranch(String version) {
        Matcher matcher = SEMANTIC_VERSION.matcher(Objects.requireNonNull(version, "version").trim());
        if (!matcher.matches()) {
            throw new MinorBranchCreatorException("Version is not a semantic patch version: " + version);
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        return major + "." + (minor + 1) + ".x";
    }
}

class MinorBranchCreatorException extends RuntimeException {

    MinorBranchCreatorException(String message) {
        super(message);
    }

    MinorBranchCreatorException(String message, Throwable cause) {
        super(message, cause);
    }
}
