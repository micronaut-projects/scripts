plugins {
    id("com.gradleup.shadow") version "9.4.1"
    id("io.micronaut.application") version "5.0.2"
}

version = "0.1"
group = "com.example"

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor("info.picocli:picocli-codegen")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    
    implementation("info.picocli:picocli")
    implementation("io.micronaut.picocli:micronaut-picocli")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut:micronaut-http-client")
    
    runtimeOnly("ch.qos.logback:logback-classic")
}

application {
    mainClass = "com.example.GithubActionsCancellerCommand"
}

java {
    sourceCompatibility = JavaVersion.toVersion("25")
    targetCompatibility = JavaVersion.toVersion("25")
}

micronaut {
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.example.*")
    }
}

