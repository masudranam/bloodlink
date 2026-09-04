package com.bloodlink.auth;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.bloodlink.auth.service.DuplicatePhoneException;
import com.bloodlink.auth.service.InvalidCredentialsException;
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

/** Turns request and auth failures into RFC 7807 problem responses. */
@RestControllerAdvice
public class AuthExceptionHandler {

    private static final String PROBLEM_BASE = "https://bloodlink.dev/problems/";

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
     * A body Jackson cannot even turn into the request object.
     *
     * <p>Two shapes reach here and both must name the field. An unknown enum
     * constant arrives as {@link InvalidFormatException}; a value rejected by a
     * {@code @JsonCreator} — such as a blood group outside the eight symbols —
     * arrives as a {@code ValueInstantiationException} instead, which is why this
     * reads the path off {@link JsonMappingException} rather than one subclass.
     *
     * <p>This also has to exist at all: deserialisation fails before bean
     * validation runs, so without a handler the exception escapes to the
     * container's {@code /error} dispatch and the caller sees a baffling 401.
     *
     * @param exception the deserialisation failure
     * @return 400 naming the field, and the accepted values where they are known
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail onUnreadableBody(HttpMessageNotReadableException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Malformed request body", "malformed-body");

        if (exception.getCause() instanceof JsonMappingException mapping) {
            String field = mapping.getPath().stream()
                    .map(reference -> reference.getFieldName() == null ? "?" : reference.getFieldName())
                    .collect(Collectors.joining("."));
            problem.setProperty("errors",
                    Map.of(field.isEmpty() ? "body" : field, describe(mapping)));
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

    /**
     * Says what would have been accepted, when that is knowable.
     *
     * @param mapping the Jackson failure
     * @return a message listing an enum's constants, or a generic rejection
     */
    private static String describe(JsonMappingException mapping) {
        if (mapping instanceof InvalidFormatException invalid) {
            Class<?> target = invalid.getTargetType();
            if (target != null && target.isEnum()) {
                // A plain enum: the constant names are the wire format.
                return "must be one of " + String.join(", ", enumNames(target));
            }
        }
        // A @JsonCreator that rejected the value, such as BloodGroup. Its own
        // message says what is accepted, which for BloodGroup is the symbols
        // rather than the constant names - listing A_POSITIVE would be worse
        // than saying nothing.
        if (rootCause(mapping) instanceof IllegalArgumentException illegal && illegal.getMessage() != null) {
            return illegal.getMessage();
        }
        return "is not a valid value";
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
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
