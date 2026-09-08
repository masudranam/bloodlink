package com.bloodlink.pledge.service;

/** Thrown when no pledge has the requested id. */
public class PledgeNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PledgeNotFoundException(long id) {
        super("No pledge with id " + id);
    }
}
