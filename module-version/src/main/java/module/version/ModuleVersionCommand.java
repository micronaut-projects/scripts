package module.version;

import io.micronaut.configuration.picocli.PicocliRunner;

import io.micronaut.core.util.CollectionUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Command(name = "module-version", description = "Find the module version in a given Micronaut release", mixinStandardHelpOptions = true)
public class ModuleVersionCommand implements Runnable {

    @Option(names = {"-m", "--micronaut"}, required = true, description = "The Micronaut release to inspect")
    String micronaut;

    @Parameters
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
            String toml = micronaut.split("\\.")[0].equals("4")
                    ? "https://repo1.maven.org/maven2/io/micronaut/platform/micronaut-platform/%s/micronaut-platform-%s.toml".formatted(micronaut, micronaut)
                    : "https://repo1.maven.org/maven2/io/micronaut/micronaut-bom/%s/micronaut-bom-%s.toml".formatted(micronaut, micronaut);
            String result = readToml(toml, module);
            System.out.println("Looking for " + module + " in Micronaut " + micronaut);
            System.out.println("Version: " + result);
        } else {
            System.out.println("No options provided. Must provide");
        }
    }

    private String readToml(String toml, String module) {
        try (InputStream s = new URL(toml).openStream()) {
            Matcher matcher = Pattern.compile("(?m)^%s[\\s]+=[\\s]+\\\"(.+)\\\"$".formatted(module)).matcher(new String(s.readAllBytes()));
            if (matcher.find()) {
                return matcher.group(1);
            }
            return "**UNKNOWN MODULE %s**".formatted(module);
        } catch (FileNotFoundException ex) {
            return "**MICRONAUT RELEASE NOT FOUND**";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
