package com.bloodlink.pledge.service;

/**
 * Thrown when somebody other than the donor who made a pledge tries to withdraw
 * it.
 *
 * Including the requester. A requester who wants rid of a pledge declines it;
 * withdrawing is the donor's own word about their own intention.
 */
public class NotThePledgingDonorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotThePledgingDonorException() {
        super("Only the donor who made this pledge may withdraw it");
    }
}
