package com.bloodlink.auth.service;

/**
 * Thrown for a failed login.
 *
 * Deliberately carries no detail about which half was wrong: an unknown phone and
 * a wrong password produce the same exception and the same response, so the API
 * cannot be used to discover who has an account.
 */
public class InvalidCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super("Invalid phone or password");
    }
}
