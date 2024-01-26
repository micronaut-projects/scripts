package platform.aligner

import com.fasterxml.jackson.dataformat.toml.TomlMapper
import io.micronaut.configuration.picocli.PicocliRunner
import java.io.File
import java.nio.file.Path
import java.util.Properties
import picocli.CommandLine.Command
import picocli.CommandLine.IVersionProvider
import picocli.CommandLine.Option
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess

private const val GRADLE_LIBS_VERSIONS_TOML = "gradle/libs.versions.toml"
private const val GRADLE_PROPERTIES = "gradle.properties"

@Command(
    name = "platform-aligner",
    description = ["""Compares the versions in a project's gradle/libs.versions.toml file with those in the platform's gradle/libs.versions.toml file and updates the project's file to match the platform's"""],
    mixinStandardHelpOptions = true,
    versionProvider = PlatformAlignerCommand::class,
)
class PlatformAlignerCommand : Runnable, IVersionProvider {

    @Option(
        names = ["-p", "--platform"],
        description = ["the location of micronaut-platform (defaults to ../micronaut-platform)"]
    )
    private var platformLocation: String = "../micronaut-platform"

    @Option(
        names = ["-m", "--module"],
        description = ["the location of the module to check (defaults to the current directory)"]
    )
    private var moduleLocation: String = "."

    @Option(
        names = ["-v", "--platformversion"],
        description = ["the platform version to use (defaults to the one in gradle.properties for the platform project)"]
    )
    private var platformVersion: String? = null

    override fun run() {
        val mapper = TomlMapper()
        val platformFile = File(platformLocation).canonicalFile.toPath()
        val platformVersionFile = platformFile.resolve(GRADLE_LIBS_VERSIONS_TOML)
        val moduleFile = File(moduleLocation).canonicalFile.toPath()
        val moduleVersionFile = moduleFile.resolve(GRADLE_LIBS_VERSIONS_TOML)

        if (platformVersionFile.exists().not()) {
            println(red("⚠️ Could not find platform versions in ${platformVersionFile.toAbsolutePath()}"))
            exitProcess(1)
        }
        if (moduleVersionFile.exists().not()) {
            println(red("⚠️ Could not find project versions in ${moduleVersionFile.toAbsolutePath()}"))
            exitProcess(1)
        }

        val platformVersionFromFile = readVersion(platformFile)
        val platformBomVersions = parseBomVersions(mapper, platformVersionFile).toMutableMap().plus(
            "io.micronaut.platform:micronaut-platform" to (platformVersion ?: platformVersionFromFile.replace("-SNAPSHOT", ""))
        ).toMap()
        val moduleBomVersions = parseBomVersions(mapper, moduleVersionFile)

        println(green("------------------------------------------------------------"))
        println(bold("  Platform version : ${yellow(platformVersionFromFile)}"))
        println(bold("  Module version  : ${yellow(readVersion(moduleFile))}"))
        println(green("------------------------------------------------------------"))
        println()

        // Find project versions that differ from those in the platform
        val differentVersions = moduleBomVersions
            .filter { platformBomVersions.containsKey(it.key) }
            .filter { platformBomVersions[it.key] != it.value }

        if (differentVersions.isNotEmpty()) {
            println(red("⚠️ Versions don't match! ${bold(" patching gradle/libs.versions.toml")}"))
            println()

            // For each bom that has a different version, find the name of the version in the project.
            // Apart from logging and test, the names are technically free-form, so we cannot assume
            val versionsAndNames =
                differentVersions.map { (k, v) -> k to (v to versionNameForBom(mapper, moduleVersionFile, k)) }.toMap()

            var moduleLibsVersionFileContents = moduleVersionFile.readText()
            versionsAndNames.forEach {
                println("${it.value.second} (${yellow(it.key)}) : ${bold(it.value.first)} -> ${bold(platformBomVersions[it.key] ?: "error")}")
                moduleLibsVersionFileContents = replace(it.value.second, it.key, moduleLibsVersionFileContents, platformBomVersions)
            }
            println()

            // Logging and Test are different
            moduleLibsVersionFileContents = replace(
                "micronaut-test",
                "io.micronaut.test:micronaut-test-bom",
                moduleLibsVersionFileContents,
                platformBomVersions
            )
            moduleLibsVersionFileContents = replace(
                "micronaut-logging",
                "io.micronaut.logging:micronaut-logging-bom",
                moduleLibsVersionFileContents,
                platformBomVersions
            )

            // Write the text back over the libs.versions.toml file
            moduleVersionFile.apply {
                writeText(moduleLibsVersionFileContents)
                println(bold(green("Updated $this")))
            }
        } else {
            println(bold(green("All versions match! 😀")))
        }
    }

    private fun replace(
        versionName: String,
        bom: String,
        moduleLibsVersionFileContents: String,
        platformBomVersions: Map<String, String>
    ) =
        moduleLibsVersionFileContents.replace(
            Regex("""$versionName\s*=\s*["'].+?["']"""),
            """$versionName = "${platformBomVersions[bom]}""""
        )

    private fun versionNameForBom(mapper: TomlMapper, versionsFile: Path, bomName: String) =
        mapper.readValue(versionsFile.readText(), Map::class.java).let { parse ->
            (parse["libraries"] as Map<*, *>)
                .entries
                .first { (it.value as Map<*, *>)["module"].toString() == bomName }
                .let {
                    (it.value as Map<*, *>).let { map ->
                        (map["version"] as Map<*, *>)["ref"].toString()
                    }
                }
        }

    private fun readVersion(moduleRoot: Path) = moduleRoot.resolve(GRADLE_PROPERTIES).let { gradleProperties ->
        Properties().apply {
            gradleProperties.toFile().inputStream().use {
                load(it)
            }
        }["projectVersion"].toString()
    }

    private fun parseBomVersions(mapper: TomlMapper, versionsFile: Path) =
        mapper.readValue(versionsFile.readText(), Map::class.java).let { parse ->
            (parse["libraries"] as Map<*, *>)
                .entries
                .filter {
                    // Only keep those that are a micronaut bom, or the platform bom
                    (it.value as Map<*, *>)["module"].toString()
                        .matches(Regex("""(io\.micronaut.*?:micronaut.+?bom|io\.micronaut\.platform:micronaut-platform)"""))
                }
                .associate {
                    (it.value as Map<*, *>).let { map ->
                        map["module"].toString() to (parse["versions"] as Map<*, *>)[(map["version"] as Map<*, *>)["ref"].toString()].toString()
                    }
                }
        }

    private fun red(msg: String) = "\u001B[31m$msg\u001B[0m"
    private fun green(msg: String) = "\u001B[32m$msg\u001B[0m"
    private fun yellow(msg: String) = "\u001B[33m$msg\u001B[0m"
    private fun bold(msg: String) = "\u001B[1m$msg\u001B[0m"

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            PicocliRunner.run(PlatformAlignerCommand::class.java, *args)
        }
    }

    override fun getVersion() = arrayOf(
        "Platform Aligner",
        "----------------",
        "Version: ${object {}.javaClass.getResourceAsStream("/version.txt")?.readAllBytes()?.decodeToString()}",
    )
}
