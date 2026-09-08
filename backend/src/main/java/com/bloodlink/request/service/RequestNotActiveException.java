package com.bloodlink.request.service;

import com.bloodlink.request.BloodRequestStatus;

/**
 * A request that has reached a terminal status was asked to do something only a
 * live request can do — searching for donors, in SPEC-007.
 *
 * <p>Distinct from {@link IllegalTransitionException}, which is about a move
 * being refused. Nothing is moving here: the request is simply finished, and its
 * status is named in the message because "conflict" on its own tells a caller
 * nothing.
 */
public class RequestNotActiveException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RequestNotActiveException(BloodRequestStatus status) {
        super("A request that is " + status + " is no longer looking for donors");
    }
}
