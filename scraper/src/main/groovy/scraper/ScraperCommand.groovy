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
        version = ["Micronaut site scraper v1.0", "©️ 2024"]
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

                // Get the source for the page... We can't use driver.getPageSource() as we wont get the <!DOCTYPE html> and the browser will go in to quirks mode
                def source = URI.create(baseUrl).resolve(path).toURL().text

                // Scripts
                downloadAsset(
                        $('script'),
                        { it.@src?.trim() },
                        this::resolve,
                        { URI.create(it).path.endsWith(".js") },
                )

                // Images
                downloadAsset(
                        $('img'),
                        { it.@src?.trim() }
                )

                // Icons
                downloadAsset(
                        $('link', rel: 'icon'),
                        { it.@href?.trim() }
                )
                downloadAsset(
                        $('link', rel: 'shortcut icon'),
                        { it.@href?.trim() }
                )

                // Css
                downloadAsset(
                        $('link', rel: 'stylesheet'),
                        { it.@href?.trim() },
                        this::resolve,
                        { URI.create(it).path.endsWith(".css") },
                )

                // RSS feed stuff
                downloadAsset(
                        $('a', href: contains('.rss')),
                        { it.@href?.trim() }
                )

                // PDFs
                downloadAsset(
                        $('a', href: contains('.pdf')),
                        { it.@href?.trim() }
                )

                // Metadata stuff
                downloadAsset(
                        $('meta', property: 'og:image'),
                        { it.@content?.trim() }
                )
                // Oembed stuff -- this modifies the page source
                downloadAsset(
                        $('link', rel: 'alternate', type: 'application/json+oembed'),
                        { it.@href?.trim() },
                        { href ->
                            def location = URLDecoder.decode((href =~ /.*embed\?url=([^&]*)/).findAll()[0][1], "UTF-8") - baseUrl
                            def resolved = resolve(href).replaceAll(/embed\?url=.+/, location + "embed.json")
                            source = source.replace('"' + href + '"', '"' + "${baseUrl}wp-json/oembed/1.0/${location}embed.json" + '"')
                            resolved
                        }
                )
                downloadAsset(
                        $('link', rel: 'alternate', type: 'text/xml+oembed'),
                        { it.@href?.trim() },
                        { href ->
                            def location = URLDecoder.decode((href =~ /.*embed\?url=([^&]*)/).findAll()[0][1], "UTF-8") - baseUrl
                            def resolved = resolve(href).replaceAll(/embed\?url=.+/, location + "embed.xml")
                            source = source.replace('"' + href.replace('&', "&amp;") + '"', '"' + "${baseUrl}wp-json/oembed/1.0/${location}embed.xml" + '"')
                            resolved
                        }
                )
                // Write source (stripping off the base URL but not the absolute slash)
                targetFile << source
                        .replace('"' + baseUrl[0..-2] + '"', '"/"')
                        .replace('"' + baseUrl[0..-2], '"')
                        .replace("'${baseUrl[0..-2]}'", "'/'")
                        .replace("'${baseUrl[0..-2]}", "'")

                // Find new exciting places to go
                $("a").each {
                    // Get link and strip off any fragment
                    String href = it.@href.split('#', 2)[0]

                    if (href.startsWith(baseUrl) && // Link to the same sits
                            !URI.create(href).path.endsWith(".pdf") && // Skip PDFs
                            !visited.contains(href) && // Not visited
                            !queue.contains(href) && // Not queued
                            !href.contains("@") && // Not an email
                            !href.startsWith(baseUrl + (baseUrl.endsWith("/") ? '' : '/') + "launch") // Not the launch page
                    ) {
                        println "Queueing new link $href from $path"
                        queue.push(href)
                    }
                }
            }
        }
    }

    private String resolve(String href) {
        String relative = href - baseUrl
        if (relative.startsWith("/")) {
            relative = relative.substring(1)
        }
        relative
    }

    @CompileStatic
    private void downloadAsset(
            Navigator selector,
            @ClosureParams(value = FirstParam) Closure<String> resourceAccessor,
            @ClosureParams(value = SimpleType, options = "java.lang.String") Closure<String> resolver = this::resolve,
            @ClosureParams(value = SimpleType, options = "java.lang.String") Closure<Boolean> filter = { true }
    ) {
        selector
                .collect(resourceAccessor)
                .findAll { it && it.startsWith(baseUrl) && !visited.contains(it) && !queue.contains(it) && !it.startsWith(baseUrl + "/launch") }
                .findAll(filter)
                .each { href ->
                    def relativeFile = targetFolder.resolve(resolver(href)).toFile()
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
        visited.add(href)
        try {
            if (!relativeFile.exists()) {
                println "Downloading $href to $relativeFile"
                relativeFile.parentFile.mkdirs()
                relativeFile.withOutputStream { os ->
                    URI.create(href).toURL().withInputStream { is ->
                        os << is
                    }
                }

                if (href.endsWith(".css")) {
                    // CSS can embed fonts and images, so scan for them, and copy them too...
                    def source = relativeFile.text
                    source.findAll(~/url\([a-zA-Z0-9\-\/]+\.(png|jpg|jpeg|gif|svg|woff2|woff|ttf|eot)/).each { asset ->
                        println "Found asset $asset in CSS $href"
                        def newUrl = URI.create(href).resolve(asset.substring(4)).toASCIIString()
                        copyAsset(newUrl, targetFolder.resolve(resolve(newUrl)).toFile())
                    }
                }
            } else {
                println "Skipping $href as $relativeFile already exists"
            }
        } catch (e) {
            // Some things are missing on the site... especially fonts in css, so log but ignore
            println "Error downloading $href: $e"
        }
    }
}
