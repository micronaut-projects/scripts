package readme.badge.replacer;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Repository(String name, @JsonProperty("clone_url") String cloneUrl, @JsonProperty("default_branch") String defaultBranch) {
}
