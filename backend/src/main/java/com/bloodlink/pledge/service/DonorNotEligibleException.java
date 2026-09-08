package com.bloodlink.pledge.service;

import java.time.LocalDate;

/**
 * Thrown when a donor may not give blood yet.
 *
 * The message names the date rather than saying "not eligible", because the
 * donor's next question is always when.
 */
public class DonorNotEligibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DonorNotEligibleException(LocalDate nextEligibleDate) {
        super("You may donate again from " + nextEligibleDate);
    }
}
