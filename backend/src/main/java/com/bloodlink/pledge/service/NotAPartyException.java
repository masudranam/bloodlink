package com.bloodlink.pledge.service;

/**
 * Thrown when somebody who is neither side of a pledge asks for its contact
 * details.
 *
 * Checked before the pledge's status, on purpose: a stranger gets 403 whatever
 * state the pledge is in, so the error cannot tell them whether two other people
 * have agreed to meet.
 */
public class NotAPartyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotAPartyException() {
        super("Only the donor and the requester of this pledge may see contact details");
    }
}
