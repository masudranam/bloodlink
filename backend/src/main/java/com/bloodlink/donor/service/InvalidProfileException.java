package com.bloodlink.donor.service;

import java.util.Map;

/**
 * Thrown for a request that is well-formed but wrong in a way bean validation
 * cannot see: an unknown thana, or a donation date in the future measured in the
 * donor's own zone rather than the server's.
 */
public class InvalidProfileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Map<String, String> errors;

    public InvalidProfileException(String field, String message) {
        super(message);
        this.errors = Map.of(field, message);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
