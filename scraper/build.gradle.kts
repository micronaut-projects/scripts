plugins {
    id("groovy") 
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.micronaut.application") version "4.3.8"
    id("com.github.erdi.webdriver-binaries") version "2.7"
}

version = "0.1"
group = "scraper"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.picocli)
    implementation(libs.micronaut.runtime.groovy)
    implementation(libs.micronaut.picocli)
    implementation(libs.micronaut.serde.jackson)
    implementation(libs.geb.core)
    implementation(libs.selenium.firefox.driver)
    implementation(libs.selenium.support)

    compileOnly(libs.picocli.codegen)
    compileOnly(libs.micronaut.serde.processor)

    runtimeOnly(libs.logback.classic)
}

webdriverBinaries {
    geckodriver("0.34.0")
}

application {
    mainClass = "scraper.ScraperCommand"
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}


micronaut {
    testRuntime("spock2")
    processing {
        incremental(true)
        annotations("scraper.*")
    }
}


tasks.named<io.micronaut.gradle.docker.NativeImageDockerfile>("dockerfileNative") {
    jdkVersion = "21"
}


