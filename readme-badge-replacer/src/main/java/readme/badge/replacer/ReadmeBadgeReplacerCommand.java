package readme.badge.replacer;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.http.HttpResponse;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

@Command(name = "readme-badge-replacer", description = "...",
        mixinStandardHelpOptions = true)
public class ReadmeBadgeReplacerCommand implements Runnable {

    @Inject
    GitubApi gitubApi;

    @Option(names = {"-v", "--verbose"}, description = "...")
    boolean verbose;

    public static void main(String[] args) throws Exception {
        PicocliRunner.run(ReadmeBadgeReplacerCommand.class, args);
    }

    public void run() {
        // Find all repositories
        for (Repository repository : gitubApi.getRepositories()) {
            if (!repository.name().startsWith("micronaut-")) continue;

            // Clone the repository
            try {
                cloneAndFix(repository);
            } catch (Exception e) {
                System.err.println(repository.name() + " failed " + e.getMessage());
            }
        }
    }

    private void cloneAndFix(Repository repository) throws IOException, InterruptedException {
        File directory = new File(repository.name());
        System.out.println("Cloning " + repository.name() + " from " + repository.cloneUrl() + " to " + directory.getAbsolutePath());
        new ProcessBuilder().command("git", "clone", "--depth", "1", repository.cloneUrl()).start().waitFor();

        // Fix the README.md
        File readme = new File(directory, "README.md");

        if (readme.exists()) {
            if (!Files.readString(readme.toPath()).contains("Revved up by Gradle Enterprise")) {
                System.out.println("Skipping " + repository.name() + " as it does not contain the badge");
                return;
            }
            new ProcessBuilder().command("git", "checkout", "-b", "develocity-badge").directory(directory).start().waitFor();

            System.out.println("Fixing README.md for " + repository.name());

            // Fix the README.md
            var patched = Files.lines(readme.toPath()).map(line -> {
                if (line.contains("Revved up by Gradle Enterprise")) {
                    return "[![Revved up by Develocity](https://img.shields.io/badge/Revved%20up%20by-Develocity-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.micronaut.io/scans)";
                } else {
                    return line;
                }
            }).collect(Collectors.joining("\n"));
            Files.writeString(readme.toPath(), patched);

            new ProcessBuilder().command("git", "commit", "-am", "Fix develocity badge").directory(directory).start().waitFor();
            new ProcessBuilder().command("git", "push", "-u", "origin", "develocity-badge").directory(directory).start().waitFor();

            HttpResponse<?> pr = gitubApi.createPr(repository.name(), new PullRequest("Gradle Enterprise rename", "develocity-badge", "master", "Fix develocity badge"));
            System.out.println(pr.code());
        }
    }
}
