plugins {
    id("groovy") 
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.micronaut.application") version "4.2.1"
}

version = "0.1"
group = "io.micronaut.scripts.license"

repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli")
    implementation("io.micronaut.groovy:micronaut-runtime-groovy")
    implementation("io.micronaut.picocli:micronaut-picocli")
    compileOnly("info.picocli:picocli-codegen")
    runtimeOnly("ch.qos.logback:logback-classic")
}


application {
    mainClass.set("io.micronaut.scripts.license.LicensePopulatorCommand")
}
java {
    sourceCompatibility = JavaVersion.toVersion("17")
    targetCompatibility = JavaVersion.toVersion("17")
}


micronaut {
    testRuntime("spock2")
    processing {
        incremental(true)
        annotations("io.micronaut.scripts.license.*")
    }
}



