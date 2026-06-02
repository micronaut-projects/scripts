package io.micronaut.scripts.github.project.exceptions;

public final class GitHubProjectMoveException extends RuntimeException {

    public GitHubProjectMoveException(String message) {
        super(message);
    }

    public GitHubProjectMoveException(String message, Throwable cause) {
        super(message, cause);
    }
}
