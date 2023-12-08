package japicmp

import io.micronaut.configuration.picocli.PicocliRunner
import java.io.File
import org.jsoup.Jsoup
import picocli.CommandLine.Command

@Command(
    name = "japicmp", description = ["..."],
    mixinStandardHelpOptions = true
)
class JapicmpCommand : Runnable {

    override fun run() {
        val reports = File("/Users/tim/Code/GitHub/micronaut-projects/micronaut-aws")
            .walk()
            .filter {
                it.name == "reports" && it.isDirectory && it.parentFile.name == "build"
            }
            .map {
                File(it, "binary-compatibility-micronaut-${it.parentFile.parentFile.name}.html")
            }
            .filter { it.exists() }
            .flatMap { Jsoup.parse(it).select(".severity-error pre").map { it.text() } }
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
