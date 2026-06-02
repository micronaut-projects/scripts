plugins {
    id("io.micronaut.minimal.application") version "5.0.0"
    id("com.gradleup.shadow") version "9.4.1"
}
version = "0.1"
group = "io.micronaut.scripts"
repositories {
    mavenCentral()
}
dependencies {
    // Logging
    runtimeOnly("ch.qos.logback:logback-classic")

    // HTP Client
    implementation("io.micronaut:micronaut-http-client")

    // CLI
    annotationProcessor("info.picocli:picocli-codegen")
    implementation("io.micronaut.picocli:micronaut-picocli")

    // Serialization
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    implementation("io.micronaut.serde:micronaut-serde-jackson")

    // Validation
    annotationProcessor("io.micronaut.validation:micronaut-validation-processor")
    implementation("io.micronaut.validation:micronaut-validation")

    // Testing
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
application {
    mainClass = "io.micronaut.scripts.github.project.commands.GithubProjectIssueMoveCommand"
}
java {
    sourceCompatibility = JavaVersion.toVersion("25")
    targetCompatibility = JavaVersion.toVersion("25")
}
micronaut {
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.github.*", "github.project.issue.move.*")
    }
}
// https://docs.gradle.org/current/userguide/upgrading_major_version_9.html#test_task_fails_when_no_tests_are_discovered
tasks.withType<AbstractTestTask>().configureEach {
    failOnNoDiscoveredTests = false
}


