/**
 * Find the version of Micronaut that contained a specific version of a module.
 *
 * Usage:
 *
 * groovy findVersion.groovy <module> <version>
 *     module: The module of interest
 *     version: (optional) The version of the module to find
 *
 * Example:
 *
 *     ❯ groovy findVersion.groovy crac 1.2.3
 *     Found micronaut-crac version 1.2.3 in 3.9.0
 *
 * Without a version, it will print all versions of the module:
 *
 *     ❯ groovy findVersion.groovy crac
 *     Found micronaut-crac version 1.0.1 in 3.7.1
 *     Found micronaut-crac version 1.0.1 in 3.7.2
 *     Found micronaut-crac version 1.0.1 in 3.7.3
 *     Found micronaut-crac version 1.0.1 in 3.7.4
 *     Found micronaut-crac version 1.0.1 in 3.7.5
 *     Found micronaut-crac version 1.0.1 in 3.7.6
 *     Found micronaut-crac version 1.0.1 in 3.7.7
 *     Found micronaut-crac version 1.1.1 in 3.8.0
 *     Found micronaut-crac version 1.1.1 in 3.8.1
 *     ...<snip>
 *
 */
import groovy.xml.*
import groovy.transform.*

@Canonical
@ToString
class Version {
    String root
    String version
    String module
    @Lazy String pom = { -> "$root/$module/$version/${module}-${version}.pom" }()
}

int versionComparator(String a, String b) {
    def aMap = a.split(/\./).collect { (it =~ /([0-9]+).*/)[0][1] }*.toInteger()
    def bMap = b.split(/\./).collect { (it =~ /([0-9]+).*/)[0][1] }*.toInteger()
    [aMap, bMap].transpose().findResult { x, y -> x <=> y ?: null } ?: aMap.size() <=> bMap.size() ?: a <=> b
}

def versions() {
    ['micronaut-platform': 'https://repo1.maven.org/maven2/io/micronaut/platform', // Post 4.0.0
     'micronaut-bom': 'https://repo1.maven.org/maven2/io/micronaut' // Pre 4.0.0
    ].collectMany { loc ->
        new XmlParser().parse("$loc.value/$loc.key/maven-metadata.xml").versioning.versions.version*.text().reverse().collect {
            new Version(root: loc.value, version: it, module: loc.key)
        }
    }.reverse()
}

def find(List<Version> versions, String module, String maybeRequestedVersion) {
    def xml = new XmlParser(false, false)
    versions.find {
        def pom = xml.parse(it.pom)
        def dep = pom.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == "micronaut-$module-bom" }
        def version = pom.properties.'*'.find { it.name() == dep?.version?.text()?.getAt(2..-2)}?.text()
        if (version) {
            if (maybeRequestedVersion && versionComparator(version, maybeRequestedVersion) >= 0 ) {
                println "Found micronaut-$module version $version in ${it.version}"
                return true // stop
            } else if (!maybeRequestedVersion) {
                println "Found micronaut-$module version $version in ${it.version}"
            }
        }
    }
}

static main(args) {
    def (module, version) = args + [null]

    def versions = versions().findAll {
        // Only search back to 3.7.0
        versionComparator(it.version.toString(), '3.7.0') > 0
    }
    find(versions, module, version)
}
