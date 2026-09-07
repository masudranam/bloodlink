package com.bloodlink.request.service;

import com.bloodlink.request.BloodRequestStatus;

/**
 * Thrown when a request is asked to make a move the state machine forbids.
 *
 * The message names both statuses, because "conflict" on its own tells a caller
 * nothing about what they got wrong.
 */
public class IllegalTransitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IllegalTransitionException(BloodRequestStatus from, BloodRequestStatus to) {
        super("A request cannot move from " + from + " to " + to);
    }
}
