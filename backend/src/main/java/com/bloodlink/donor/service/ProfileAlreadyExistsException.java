package com.bloodlink.donor.service;

/** Thrown when a donor who already has a profile tries to create a second one. */
public class ProfileAlreadyExistsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProfileAlreadyExistsException(String message) {
        super(message);
    }
}
