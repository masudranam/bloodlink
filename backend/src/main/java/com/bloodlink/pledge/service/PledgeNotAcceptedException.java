package com.bloodlink.pledge.service;

import com.bloodlink.pledge.PledgeStatus;

/**
 * Thrown when contact details are asked for on a pledge that has not been
 * accepted.
 *
 * This is the privacy rule at its narrowest point: an offer is not an
 * introduction, and the requester saying yes is the whole difference.
 */
public class PledgeNotAcceptedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PledgeNotAcceptedException(PledgeStatus status) {
        super("Contact details are shared only for an accepted pledge; this one is " + status);
    }
}
