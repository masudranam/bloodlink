package com.roktolink.auth;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.roktolink.auth.service.DuplicatePhoneException;
import com.roktolink.auth.service.InvalidCredentialsException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns auth failures into RFC 7807 problem responses. */
@RestControllerAdvice
public class AuthExceptionHandler {

    private static final String PROBLEM_BASE = "https://roktolink.dev/problems/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onValidationFailure(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError field : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(field.getField(), field.getDefaultMessage());
        }
        exception.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "validation-failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * A body Jackson cannot even turn into the request object — most often an
     * unknown enum value such as {@code "role": "ADMIN"}.
     *
     * <p>This fails before bean validation runs, so without this handler the
     * exception escapes to the container's {@code /error} dispatch, which the
     * stateless filter chain refuses, and the caller sees a baffling 401 instead
     * of being told which field is wrong.
     *
     * @param exception the deserialisation failure
     * @return 400 naming the field, and the values that would have been accepted
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail onUnreadableBody(HttpMessageNotReadableException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Malformed request body", "malformed-body");

        if (exception.getCause() instanceof InvalidFormatException invalid) {
            String field = invalid.getPath().stream()
                    .map(reference -> reference.getFieldName() == null ? "?" : reference.getFieldName())
                    .collect(Collectors.joining("."));
            Class<?> target = invalid.getTargetType();
            String allowed = target != null && target.isEnum()
                    ? " must be one of " + String.join(", ", enumNames(target))
                    : " is not a valid value";
            problem.setProperty("errors", Map.of(field.isEmpty() ? "body" : field, allowed.trim()));
        }
        return problem;
    }

    @ExceptionHandler(DuplicatePhoneException.class)
    ProblemDetail onDuplicatePhone(DuplicatePhoneException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), "duplicate-phone");
    }

    /**
     * One response for every failed login, whatever the reason.
     *
     * @param exception the failure, whose message is deliberately generic
     * @return an identical 401 for an unknown phone and for a wrong password
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail onInvalidCredentials(InvalidCredentialsException exception) {
        return problem(HttpStatus.UNAUTHORIZED, exception.getMessage(), "invalid-credentials");
    }

    private static String[] enumNames(Class<?> enumType) {
        Object[] constants = enumType.getEnumConstants();
        String[] names = new String[constants.length];
        for (int i = 0; i < constants.length; i++) {
            names[i] = ((Enum<?>) constants[i]).name();
        }
        return names;
    }

    private ProblemDetail problem(HttpStatus status, String detail, String slug) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create(PROBLEM_BASE + slug));
        return problem;
    }
}
