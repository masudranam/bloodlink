package com.bloodlink.request.service;

/**
 * Thrown when a requester tries to act on a request somebody else raised.
 *
 * Deliberately separate from "not found": the request exists and the caller is
 * authenticated, they simply have no business touching it.
 */
public class NotTheRequesterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotTheRequesterException() {
        super("Only the requester who raised this request may change it");
    }
}
