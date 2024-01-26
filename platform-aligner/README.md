This command-line utility aligns the versions of libraries used in a project with those specified in a platform's `gradle/libs.versions.toml` file.

The application provides three command-line options:

- `--platform` or `-p`: the location of the platform project (default is `../micronaut-platform`).
- `--module` or `-m`: the location of the module to check (default is the current directory).
- `--platformversion` or `-v`: the platform version to use (default is the one in `gradle.properties` for the platform project with any `-SNAPSHOT` removed).

It compares versions in the module with those found in the platform, and updates the module's `gradle/libs.versions.toml` file with the version from platform if they differ.

### Example

This example uses the native binary (generated via `./gradlew nativeCompile) and assumes

1. You are in the micronaut-aws project directory.
2. You have a micronaut-platform project in the parent directory.

```text
❯ ~/bin/platform-aligner
10:15:13.901 [main] INFO  i.m.c.DefaultApplicationContext$RuntimeConfiguredEnvironment - Established active environments: [cli]
------------------------------------------------------------
  Platform version : 4.3.0-SNAPSHOT
  Module version  : 4.3.0-SNAPSHOT
------------------------------------------------------------

⚠️ Versions don't match!  patching gradle/libs.versions.toml

micronaut (io.micronaut:micronaut-core-bom) : 4.2.2 -> 4.2.3
micronaut-discovery (io.micronaut.discovery:micronaut-discovery-client-bom) : 4.1.0 -> 4.2.0
micronaut-groovy (io.micronaut.groovy:micronaut-groovy-bom) : 4.1.0 -> 4.2.0
micronaut-mongodb (io.micronaut.mongodb:micronaut-mongo-bom) : 5.1.0 -> 5.2.0
micronaut-reactor (io.micronaut.reactor:micronaut-reactor-bom) : 3.2.0 -> 3.2.1
micronaut-serde (io.micronaut.serde:micronaut-serde-bom) : 2.5.1 -> 2.7.0
micronaut-servlet (io.micronaut.servlet:micronaut-servlet-bom) : 4.3.0 -> 4.4.0
micronaut-security (io.micronaut.security:micronaut-security-bom) : 4.4.0 -> 4.5.0
micronaut-views (io.micronaut.views:micronaut-views-bom) : 5.0.1 -> 5.1.0

Updated gradle/libs.versions.toml
```