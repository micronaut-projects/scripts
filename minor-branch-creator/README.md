## Micronaut 5.0.0-RC1 Documentation

## Usage

```bash
./gradlew run --args="--url=https://github.com/micronaut-projects/micronaut-platform/blob/5.0.x/gradle/libs.versions.toml --repository=micronaut-projects/micronaut-security --set-default"
```

Omit `--repository` to run against every repository configured in `githuborg.repos`. Repository names without an owner are resolved under `micronaut-projects`.

Add `--dry-run` to print the branch creation and default branch update actions without changing the GitHub repository.

The command reads `GITHUB_TOKEN` or `GH_TOKEN` by default. You can also pass `--github-token=...`.

You can pass the TOML location as `--url=...` or `-u ...`.

The token needs permission to read the platform TOML file, read the target repository metadata, create refs, and update the repository default branch when `--set-default` is used.

- [User Guide](https://docs.micronaut.io/5.0.0-RC1/guide/index.html)
- [API Reference](https://docs.micronaut.io/5.0.0-RC1/api/index.html)
- [Configuration Reference](https://docs.micronaut.io/5.0.0-RC1/guide/configurationreference.html)
- [Micronaut Guides](https://guides.micronaut.io/index.html)
---

- [Shadow Gradle Plugin](https://gradleup.com/shadow/)
- [Micronaut Gradle Plugin documentation](https://micronaut-projects.github.io/micronaut-gradle-plugin/latest/)
- [GraalVM Gradle Plugin documentation](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html)
## Feature serialization-jackson documentation


- [Micronaut Serialization Jackson Core documentation](https://micronaut-projects.github.io/micronaut-serialization/latest/guide/)


## Feature http-client documentation


- [Micronaut HTTP Client documentation](https://docs.micronaut.io/latest/guide/index.html#nettyHttpClient)
