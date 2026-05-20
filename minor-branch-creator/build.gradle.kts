plugins {
    id("io.micronaut.application") version "5.0.0-M1"
    id("com.gradleup.shadow") version "9.4.1"
}

version = "0.1"
group = "minor.branch.creator"



repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor("info.picocli:picocli-codegen")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    implementation("info.picocli:picocli")
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut.picocli:micronaut-picocli")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    runtimeOnly("ch.qos.logback:logback-classic")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}



application {
    mainClass = "minor.branch.creator.MinorBranchCreatorCommand"
}

java {
    sourceCompatibility = JavaVersion.toVersion("25")
    targetCompatibility = JavaVersion.toVersion("25")
}




graalvmNative {
    binaries {
        all {
            buildArgs.add("-H:+SharedArenaSupport")
        }
    }
}




micronaut {
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("minor.branch.creator.*")
    }
}

tasks.named<io.micronaut.gradle.docker.MicronautDockerfile>("dockerfile") {

    baseImage = "eclipse-temurin:25-jre"
}







// https://docs.gradle.org/current/userguide/upgrading_major_version_9.html#test_task_fails_when_no_tests_are_discovered
tasks.withType<AbstractTestTask>().configureEach {
    failOnNoDiscoveredTests = false
}




