# GitHub Actions Workflow Canceller

A Micronaut CLI application to cancel all queued and in-progress GitHub Actions workflow runs for a given repository.

## Features

- Cancel all queued, pending, waiting, and in-progress workflow runs
- Dry-run mode to preview what would be cancelled
- Uses GitHub's REST API with declarative Micronaut HTTP client
- Supports native compilation with GraalVM

## Requirements

- Java 21+
- GitHub Personal Access Token with `repo` scope (or `actions:write` for public repos)

## Building

```bash
# Build the JAR
./gradlew shadowJar

# Build native executable (requires GraalVM)
./gradlew nativeCompile
```

## Usage

### Using the JAR

```bash
# Set token via environment variable
export GITHUB_TOKEN=ghp_xxxxx

# Cancel all runs
java -jar build/libs/github-actions-canceller-0.1-all.jar micronaut-projects/micronaut-core

# Dry run (list what would be cancelled)
java -jar build/libs/github-actions-canceller-0.1-all.jar -d micronaut-projects/micronaut-core

# Pass token directly
java -jar build/libs/github-actions-canceller-0.1-all.jar -t ghp_xxxxx micronaut-projects/micronaut-core
```

### Using the Native Executable

```bash
./gh-actions-cancel micronaut-projects/micronaut-core
```

### Cancelling all Micronaut repositories

```bash
export GITHUB_TOKEN=ghp_xxxxx
./cancel-all-workflows.sh
```

The script reads repository names from the `slug` entries in
`micronaut-docs-index/modules.yml`, skips `micronaut-core`, and invokes the
fat JAR once per repository. Use `./cancel-all-workflows.sh --dry-run` to list
the runs without cancelling them.

### Options

```
Usage: gh-actions-cancel [-dhvV] [-t=<token>] <repository>

Cancel all queued and in-progress GitHub Actions workflow runs for a repository.

      <repository>   Repository in 'owner/repo' format
  -d, --dry-run      List runs that would be cancelled without cancelling
  -h, --help         Show this help message and exit
  -t, --token=<token>
                     GitHub personal access token (or use GITHUB_TOKEN env var)
  -v, --verbose      Enable verbose output
  -V, --version      Print version information and exit
```

## Example Output

```
Repository: micronaut-projects/micronaut-core

Found 3 cancellable workflow run(s).
Cancelling run #4523 (Java CI) on branch 'feature/xyz' [queued]...
  ✓ Cancelled successfully
Cancelling run #4522 (Java CI) on branch 'feature/abc' [in_progress]...
  ✓ Cancelled successfully
Cancelling run #4521 (Native Tests) on branch 'main' [queued]...
  ✓ Cancelled successfully

Done! Cancelled 3 workflow run(s).
```

## Creating a GitHub Token

1. Go to GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate new token with:
   - `repo` scope (for private repositories)
   - Or just `public_repo` and `workflow` (for public repositories)

## License

Apache 2.0
