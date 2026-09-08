package com.bloodlink.pledge.service;

/**
 * Thrown when a donor pledges twice against one request.
 *
 * The service checks first so the caller gets this rather than a constraint
 * violation, but the unique index on (request_id, donor_id) is the real guard:
 * two concurrent pledges would both pass the check and only one can insert.
 */
public class AlreadyPledgedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AlreadyPledgedException(long requestId) {
        super("You have already pledged against request " + requestId);
    }
}
