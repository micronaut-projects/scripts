package github.project.issue.move;

import com.github.MoveRequest;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoveRequestTest {

    @Test
    void validatesRequiredFields() {
        try (ApplicationContext context = ApplicationContext.run(Environment.TEST)) {
            Validator validator = context.getBean(Validator.class);

            Set<ConstraintViolation<MoveRequest>> violations = validator.validate(new MoveRequest(
                    "",
                    " ",
                    null,
                    ""));

            assertEquals(Set.of("destinationProject", "githubToken", "organization", "sourceProject"), violations.stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .collect(Collectors.toSet()));
        }
    }
}
