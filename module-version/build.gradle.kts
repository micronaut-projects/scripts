plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.micronaut.application") version "4.3.3"
}

version = "0.1"
group = "module.version"

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor("info.picocli:picocli-codegen")

    implementation("info.picocli:picocli")
    implementation("io.micronaut.picocli:micronaut-picocli")

    runtimeOnly("ch.qos.logback:logback-classic")
}

application {
    mainClass.set("module.version.ModuleVersionCommand")
}

java {
    sourceCompatibility = JavaVersion.toVersion("17")
    targetCompatibility = JavaVersion.toVersion("17")
}

micronaut {
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("module.version.*")
    }
}



