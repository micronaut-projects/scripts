package module.version;

import io.micronaut.configuration.picocli.PicocliRunner;

import io.micronaut.core.util.CollectionUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Command(name = "module-version", description = "Find the module version in a given Micronaut release", mixinStandardHelpOptions = true)
public class ModuleVersionCommand implements Runnable {

    @Option(names = {"-m", "--micronaut"}, required = true, description = "The Micronaut release to inspect")
    String micronaut;

    @Parameters
    @SuppressWarnings("unused")
    private List<String> spec;

    public static void main(String[] args) {
        PicocliRunner.run(ModuleVersionCommand.class, args);
    }

    public void run() {
        if (CollectionUtils.isEmpty(spec)) {
            System.out.println("No module name provided");
            System.exit(1);
        }
        String module = spec.get(0);
        if (micronaut != null) {
            String toml = getTomlUrl(module);
            String result = readToml(toml, module);
            System.out.println(bold(yellow("Version: " + result)));
        } else {
            System.out.println("No options provided. Must provide");
        }
    }

    private String getTomlUrl(String module) {
        int major = Integer.parseInt(micronaut.split("\\.")[0]);
        if (major >= 4) {
            System.out.println(green("Searching platform for %s %s %s".formatted(module, (isBranch() ? "on branch" : "in release"), micronaut)));
            if (isBranch()) {
                return "https://raw.githubusercontent.com/micronaut-projects/micronaut-platform/%s/gradle/libs.versions.toml".formatted(micronaut);
            } else {
                return "https://repo1.maven.org/maven2/io/micronaut/platform/micronaut-platform/%s/micronaut-platform-%s.toml".formatted(micronaut, micronaut);
            }
        } else {
            System.out.println(green("Searching core for %s %s %s".formatted(module, (isBranch() ? "on branch" : "in release"), micronaut)));
            if (isBranch()) {
                return "https://raw.githubusercontent.com/micronaut-projects/micronaut-core/%s/gradle/libs.versions.toml".formatted(micronaut);
            } else {
                return "https://repo1.maven.org/maven2/io/micronaut/micronaut-bom/%s/micronaut-bom-%s.toml".formatted(micronaut, micronaut);
            }
        }
    }

    private boolean isBranch() {
        return micronaut.endsWith(".x");
    }

    private String red(String msg) { return "\u001B[31m%s\u001B[0m".formatted(msg); }
    private String green(String msg) { return "\u001B[32m%s\u001B[0m".formatted(msg); }
    private String yellow(String msg) { return "\u001B[33m%s\u001B[0m".formatted(msg); }
    private String bold(String msg) { return "\u001B[1m%s\u001B[0m".formatted(msg); }

    private String readToml(String toml, String module) {
        try (InputStream s = new URL(toml).openStream()) {
            Matcher matcher = Pattern.compile("(?m)^%s[\\s]+=[\\s]+\\\"(.+)\\\"$".formatted((isBranch() ? "managed-" : "") + module)).matcher(new String(s.readAllBytes()));
            if (matcher.find()) {
                return matcher.group(1);
            }
            return red("**UNKNOWN MODULE %s**".formatted(module));
        } catch (FileNotFoundException ex) {
            return red("**MICRONAUT RELEASE NOT FOUND**");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
