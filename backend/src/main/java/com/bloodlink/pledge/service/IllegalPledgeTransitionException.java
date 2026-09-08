package com.bloodlink.pledge.service;

import com.bloodlink.pledge.PledgeStatus;

/**
 * Thrown when a pledge is asked to make a move the state machine forbids.
 *
 * The message names both statuses, because "conflict" on its own tells a caller
 * nothing about what they got wrong.
 */
public class IllegalPledgeTransitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IllegalPledgeTransitionException(PledgeStatus from, PledgeStatus to) {
        super("A pledge cannot move from " + from + " to " + to);
    }
}
