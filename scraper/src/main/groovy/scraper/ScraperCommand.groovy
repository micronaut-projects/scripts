package scraper

import geb.Browser
import geb.navigator.Navigator
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import groovy.transform.stc.SimpleType
import io.micronaut.configuration.picocli.PicocliRunner
import picocli.CommandLine.Command
import picocli.CommandLine.Option

import java.nio.file.Path

@Command(
        name = 'scraper',
        description = 'Scrape the micronaut.io website into a local folder',
        mixinStandardHelpOptions = true,
        version = [ "Micronaut site scraper v1.0", "©️ 2024" ]
)
class ScraperCommand implements Runnable {

    private String baseUrl
    private final Set<String> visited = []
    private final Queue<String> queue = new LinkedList<>()

    @Option(names = ['-t', '--target'], description = 'target output folder')
    private Path targetFolder = Path.of("site")

    static void main(String[] args) throws Exception {
        PicocliRunner.run(ScraperCommand.class, args)
    }

    void run() {
        process(targetFolder)
    }

    private void process(Path targetFolder) {
        this.targetFolder = targetFolder
        println "Will save to $targetFolder"
        queue.offer("")
        Browser.drive {
            this.baseUrl = config.rawConfig['baseUrl']
            if (!baseUrl.endsWith('/')) {
                baseUrl += '/'
            }
            while (!queue.isEmpty()) {
                String path = queue.pop()
                String target = path - baseUrl
                if (!target.endsWith(".html")) {
                    target += (target.endsWith("/") ? '' : '/') + "index.html"
                }
                if (target.startsWith("/")) {
                    target = target.substring(1)
                }
                def targetFile = targetFolder.resolve(target).toFile()
                targetFile.parentFile.mkdirs()
                // Check we haven't moved out of the parent folder by accident or some crafted html
                if (isOutside(targetFolder, targetFile)) {
                    println "Skipping $path as it would write outside of $targetFolder"
                }
                if (visited.contains(path)) {
                    continue
                }
                println "Visiting $path ==> $target"
                visited.add(path)
                // Feed is a special case
                if (path.startsWith(baseUrl + (baseUrl.endsWith("/") ? '' : '/') + "feed/rss")) {
                    copyAsset(path, targetFolder.resolve("feed/rss.xml").toFile())
                    continue
                }
                go(path)
                targetFile << driver.getPageSource()
                downloadAsset(
                        $('script'),
                        { it.@src?.trim() },
                        { URI.create(it).path.endsWith(".js") },
                )
                downloadAsset(
                        $('img'),
                        { it.@src?.trim() },
                        { true },
                )
                downloadAsset(
                        $('link', rel: 'stylesheet'),
                        { it.@href?.trim() },
                        { URI.create(it).path.endsWith(".css") },
                )
                downloadAsset(
                        $('a', href: contains('.rss')),
                        { it.@href?.trim() },
                        { true },
                )
                // Find new exciting places to go
                $("a").each {
                    // Get link and strip off any fragment
                    String href = it.@href.split('#', 2)[0]

                    if (href.startsWith(baseUrl) &&
                            !URI.create(href).path.endsWith(".pdf") &&
                            !visited.contains(href) &&
                            !queue.contains(href) &&
                            !href.startsWith(baseUrl + (baseUrl.endsWith("/") ? '' : '/') + "launch")
                    ) {
                        println "Queueing new link $href from $path"
                        queue.push(href)
                    }
                }
            }
        }
    }

    @CompileStatic
    private void downloadAsset(
            Navigator selector,
            @ClosureParams(value = FirstParam) Closure<String> resourceAccessor,
            @ClosureParams(value = SimpleType, options = "java.lang.String") Closure<Boolean> filter
    ) {
        selector
                .collect(resourceAccessor)
                .findAll { it && it.startsWith(baseUrl) && !visited.contains(it) && !queue.contains(it) && !it.startsWith(baseUrl + "/launch") }
                .findAll(filter)
                .each { href ->
                    visited.add(href)
                    String relative = href - baseUrl
                    if (relative.startsWith("/")) {
                        relative = relative.substring(1)
                    }
                    def relativeFile = targetFolder.resolve(relative).toFile()
                    copyAsset(href, relativeFile)
                }
    }

    private static boolean isOutside(Path path, File file) {
        !file.absolutePath.startsWith(path.toAbsolutePath().toString())
    }

    @CompileStatic
    private void copyAsset(String href, File relativeFile) {
        if (isOutside(targetFolder, relativeFile)) {
            println "Skipping $href as it would write outside of $targetFolder"
            return
        }
        if (!relativeFile.exists()) {
            println "Downloading $href to $relativeFile"
            relativeFile.parentFile.mkdirs()
            URI.create(href).toURL().withInputStream {
                relativeFile.withOutputStream { os -> os << it }
            }
        } else {
            println "Skipping $href as $relativeFile already exists"
        }
    }
}
