package com.bloodlink.pledge.service;

/**
 * Thrown when a donor with no profile tries to pledge.
 *
 * There is no blood group to check compatibility against and no donation date to
 * check eligibility against, so this is refused rather than guessed at.
 */
public class DonorProfileRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DonorProfileRequiredException() {
        super("Create your donor profile before pledging");
    }
}
