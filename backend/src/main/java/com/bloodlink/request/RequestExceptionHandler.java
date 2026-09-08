package com.bloodlink.request;

import com.bloodlink.request.service.IllegalTransitionException;
import com.bloodlink.request.service.InvalidRequestException;
import com.bloodlink.request.service.NotTheRequesterException;
import com.bloodlink.request.service.RequestNotActiveException;
import com.bloodlink.request.service.RequestNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns blood request failures into RFC 7807 problem responses. */
@RestControllerAdvice
public class RequestExceptionHandler {

    private static final String PROBLEM_BASE = "https://bloodlink.dev/problems/";

    /**
     * A refused lifecycle move.
     *
     * <p>409 rather than 400: the request is well-formed and the caller is
     * allowed, the request simply is not where they think it is. The detail names
     * both statuses, because "conflict" alone tells a caller nothing.
     *
     * @param exception the refused transition
     * @return 409 naming the current and attempted statuses
     */
    @ExceptionHandler(IllegalTransitionException.class)
    ProblemDetail onIllegalTransition(IllegalTransitionException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), "illegal-transition");
    }

    /**
     * Somebody else's request.
     *
     * <p>403 rather than 404: hiding the existence of a request would be security
     * theatre here — the feed is readable by every authenticated user anyway.
     *
     * @param exception the ownership failure
     * @return 403
     */
    @ExceptionHandler(NotTheRequesterException.class)
    ProblemDetail onNotTheRequester(NotTheRequesterException exception) {
        return problem(HttpStatus.FORBIDDEN, exception.getMessage(), "not-the-requester");
    }

    /**
     * A request that is over.
     *
     * <p>409 for the same reason a refused transition is: the caller is allowed
     * and the body is fine, the request has simply finished. Distinguished from
     * an illegal transition by its own problem type, because a client showing a
     * search screen wants to react differently to "that request is closed" than
     * to "that move is not allowed".
     *
     * @param exception the finished request
     * @return 409 naming the terminal status
     */
    @ExceptionHandler(RequestNotActiveException.class)
    ProblemDetail onRequestNotActive(RequestNotActiveException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), "request-not-active");
    }

    @ExceptionHandler(RequestNotFoundException.class)
    ProblemDetail onNotFound(RequestNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage(), "request-not-found");
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail onInvalidRequest(InvalidRequestException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "validation-failed");
        problem.setProperty("errors", exception.getErrors());
        return problem;
    }

    /**
     * Query parameters rejected by {@code @Validated} on the controller, such as a
     * page size over 100.
     *
     * @param exception the violation
     * @return 400 with the same errors shape body validation produces
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail onConstraintViolation(ConstraintViolationException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            String path = violation.getPropertyPath().toString();
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            errors.putIfAbsent(field, violation.getMessage());
        }

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "validation-failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String detail, String slug) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create(PROBLEM_BASE + slug));
        return problem;
    }
}
