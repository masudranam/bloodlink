package com.bloodlink.request.service;

/** Thrown when there is no request with the given id. */
public class RequestNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RequestNotFoundException(long id) {
        super("No blood request with id " + id);
    }
}
