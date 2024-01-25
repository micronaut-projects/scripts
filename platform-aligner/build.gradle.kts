import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.9.21"
    id("com.google.devtools.ksp") version "1.9.21-1.0.16"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.micronaut.application") version "4.2.1"
}

version = "0.2"
group = "platform.aligner"

val kotlinVersion = project.properties["kotlinVersion"]

repositories {
    mavenCentral()
}

dependencies {
    ksp("info.picocli:picocli-codegen")

    implementation("info.picocli:picocli")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.picocli:micronaut-picocli")
    implementation("org.jetbrains.kotlin:kotlin-reflect:${kotlinVersion}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinVersion}")
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.16.1"))
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-toml")

    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
}

sourceSets {
    val main by getting {
        resources.srcDir(layout.buildDirectory.dir("generated-resources"))
    }
}

tasks {
    val writeVersion by registering {
        val output = layout.buildDirectory.file("generated-resources/version.txt")
        outputs.file(output)
        doLast {
            output.get().asFile.writeText(version.toString())
        }
    }
    val processResources by getting {
        dependsOn(writeVersion)
    }
    val inspectRuntimeClasspath by getting {
        dependsOn(writeVersion)
    }
}

application {
    mainClass.set("platform.aligner.PlatformAlignerCommand")
}

java {
    sourceCompatibility = JavaVersion.toVersion("17")
}

micronaut {
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("platform.aligner.*")
    }
}



