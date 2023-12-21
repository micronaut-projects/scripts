package io.micronaut.scripts.license

import groovy.io.FileType
import io.micronaut.configuration.picocli.PicocliRunner
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(name = 'license-populator', description = '...',
        mixinStandardHelpOptions = true)
class LicensePopulatorCommand implements Runnable {

    private static final String EXTENSION_JAVA = ".java"
    private static final String EXTENSION_GROOVY = ".groovy"
    private static final String EXTENSION_KT = ".kt"

    @Option(names = ['-v', '--verbose'], description = 'verbose output when executing the command')
    boolean verbose

    @Option(names = ['-h', '--header'], description = 'License Header File absolute path', defaultValue = '/Users/sdelamo/github/micronaut-projects/micronaut-guides/buildSrc/src/main/resources/LICENSEHEADER')
    String headerFile

    @Option(names = ['-f', '--folder'], description = 'source code folder', defaultValue = '/Users/sdelamo/github/micronaut-projects/micronaut-guides/guides/')
    String folder


    static void main(String[] args) throws Exception {
        PicocliRunner.run(LicensePopulatorCommand.class, args)
    }

    void run() {
        File dir = new File(folder)
        if (!dir.exists()) {
            println "Folder " + folder + " does not exists"
            return
        }
        File licenseHeaderFile = new File(headerFile)
        if (!licenseHeaderFile.exists()) {
            println "License Header file " + headerFile + " does not exists"
            return
        }
        String licenseHeaderText = licenseHeaderFile.text
        dir.eachFileRecurse (FileType.FILES) { file ->
            if (file.path.endsWith(EXTENSION_JAVA) || file.path.endsWith(EXTENSION_GROOVY) || file.path.endsWith(EXTENSION_KT)) {
                if (verbose) {
                    println "Processing: " + file.path
                }
                if (!file.text.contains("Licensed under")) {
                    file.text = licenseHeaderText + file.text
                } else {
                    if (verbose) {
                        println "Already contains license. Skipping: " + file.path
                    }
                }

            }
        }
    }
}
