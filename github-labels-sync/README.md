# GitHub Labels Sync

Creates the standard Micronaut issue labels in a GitHub repository. Labels that already exist (compared case-insensitively by name) are left untouched. The label set lives in `src/main/resources/application.properties` under `github.labels.*`.

## Usage with Native Image

```
./gradlew nativeCompile
build/native/nativeCompile/gitHub-labels-sync --args="[-o <org>] [-t <token>] [-n] [-v] <repository>"
```

## Usage with Gradle Task

```
./gradlew run --args="[-o <org>] [-t <token>] [-n] [-v] <repository>"
```

| Argument | Description |
|---|---|
| `<repository>` | Repository slug, e.g. `micronaut-core`. Required. |
| `-o, --organization` | GitHub organization. Defaults to `micronaut-projects`. |
| `-t, --token` | GitHub token with `repo` (or `issues: write`) permission. Defaults to the `GITHUB_TOKEN` environment variable. |
| `-n, --dry-run` | Only report which labels would be created. |
| `-v, --verbose` | Also list the labels that already existed. |

Examples:

```
GITHUB_TOKEN=$(gh auth token) ./gradlew run --args="micronaut-core"
./gradlew run --args="-o my-org --dry-run my-repo"
```

Or build a fat jar with `./gradlew shadowJar` and run `java -jar build/libs/github-labels-sync-0.1-all.jar micronaut-core`.

## Micronaut 5.2.2 Documentation

- [User Guide](https://docs.micronaut.io/5.2.2/guide/index.html)
- [API Reference](https://docs.micronaut.io/5.2.2/api/index.html)
- [Configuration Reference](https://docs.micronaut.io/5.2.2/guide/configurationreference.html)
- [Micronaut Guides](https://guides.micronaut.io/index.html)
---

- [Shadow Gradle Plugin](https://gradleup.com/shadow/)
- [Micronaut Gradle Plugin documentation](https://micronaut-projects.github.io/micronaut-gradle-plugin/latest/)
- [GraalVM Gradle Plugin documentation](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html)
## Feature serialization-jackson documentation


- [Micronaut Serialization Jackson Core documentation](https://micronaut-projects.github.io/micronaut-serialization/latest/guide/)


