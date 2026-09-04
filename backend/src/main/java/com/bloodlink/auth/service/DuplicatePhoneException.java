package com.bloodlink.auth.service;

/** Thrown when a registration uses a phone number that already has an account. */
public class DuplicatePhoneException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicatePhoneException(String message) {
        super(message);
    }
}
