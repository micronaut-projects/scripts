package readme.badge.replacer;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record PullRequest(
        String title,
        String head,
        String base,
        String body
) {
}
