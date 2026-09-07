package com.bloodlink.request.service;

import java.util.Map;

/**
 * Thrown for a request that is well-formed but wrong in a way bean validation
 * cannot see: an unknown hospital, or a date that has already passed.
 */
public class InvalidRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Map<String, String> errors;

    public InvalidRequestException(String field, String message) {
        super(message);
        this.errors = Map.of(field, message);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
