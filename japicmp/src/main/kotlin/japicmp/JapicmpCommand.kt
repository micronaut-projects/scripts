package japicmp

import io.micronaut.configuration.picocli.PicocliRunner
import java.io.File
import org.jsoup.Jsoup
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "japicmp", description = ["Scans the parent project directory for japicmp reports and outputs the errors to be placed in the accepted-api-changes json file"],
    mixinStandardHelpOptions = true
)
class JapicmpCommand : Runnable {

    @Option(names = ["-d", "--directory"], required = false, description = ["Root to the directory to scan for japicmp reports"])
    var rootLocation: String = "."

    @Option(names = ["-v", "--verbose"], required = false, description = ["Verbose output"])
    var verbose = false

    override fun run() {
        val location = File(rootLocation).absoluteFile
        println("Scanning ${location.absolutePath} for japicmp reports")
        val reports = location
            .walk()
            .filter {
                if (verbose) {
                    println("Checking ${it.absolutePath}")
                }
                it.name == "reports" && it.isDirectory && it.parentFile.name == "build"
            }
            .map {
                listOf(
                    File(it, "binary-compatibility-micronaut-${it.parentFile.parentFile.name}.html"),
                    File(it, "binary-compatibility-${it.parentFile.parentFile.name}.html")
                )
                    .find { f -> f.exists() }
            }
            .filterNotNull()
            .flatMap {
                if (verbose) {
                    println("Parsing ${it.absolutePath}")
                }
                Jsoup.parse(it).select(".severity-error pre").map { it.text() }
            }
            .filter { it.isNotEmpty() }
            .joinToString(",\n")
        if (reports.isNotEmpty()) {
            println(reports)
        } else {
            println("\u001B[31mNo binary compatibility errors found\u001B[0m")
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            PicocliRunner.run(JapicmpCommand::class.java, *args)
        }
    }
}
