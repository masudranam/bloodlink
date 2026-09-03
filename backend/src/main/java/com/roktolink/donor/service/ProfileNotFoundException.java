package com.roktolink.donor.service;

/** Thrown when a donor has no profile to read, replace or delete. */
public class ProfileNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProfileNotFoundException(String message) {
        super(message);
    }
}
