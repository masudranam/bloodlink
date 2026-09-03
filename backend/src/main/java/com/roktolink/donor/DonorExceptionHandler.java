package com.roktolink.donor;

import com.roktolink.donor.service.InvalidProfileException;
import com.roktolink.donor.service.ProfileAlreadyExistsException;
import com.roktolink.donor.service.ProfileNotFoundException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns donor profile failures into RFC 7807 problem responses. */
@RestControllerAdvice
public class DonorExceptionHandler {

    private static final String PROBLEM_BASE = "https://roktolink.dev/problems/";

    @ExceptionHandler(ProfileAlreadyExistsException.class)
    ProblemDetail onAlreadyExists(ProfileAlreadyExistsException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), "donor-profile-exists");
    }

    @ExceptionHandler(ProfileNotFoundException.class)
    ProblemDetail onNotFound(ProfileNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage(), "donor-profile-not-found");
    }

    /**
     * Well-formed but wrong: an unknown thana, or a donation date in the future.
     *
     * @param exception the failure, carrying the field it concerns
     * @return 400 with the same {@code errors} shape bean validation produces, so
     *         a client has one thing to parse rather than two
     */
    @ExceptionHandler(InvalidProfileException.class)
    ProblemDetail onInvalidProfile(InvalidProfileException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "validation-failed");
        problem.setProperty("errors", exception.getErrors());
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String detail, String slug) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create(PROBLEM_BASE + slug));
        return problem;
    }
}
