package io.micronaut.scripts.github.project.services;

import com.github.MoveRequest;
import io.micronaut.scripts.github.project.models.MoveSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface ProjectMover {

    MoveSummary move(@NotNull @Valid MoveRequest request);
}
