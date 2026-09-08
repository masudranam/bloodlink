package com.bloodlink.pledge;

import com.bloodlink.pledge.service.AlreadyPledgedException;
import com.bloodlink.pledge.service.DonorNotEligibleException;
import com.bloodlink.pledge.service.DonorProfileRequiredException;
import com.bloodlink.pledge.service.IllegalPledgeTransitionException;
import com.bloodlink.pledge.service.IncompatibleBloodGroupException;
import com.bloodlink.pledge.service.NotAPartyException;
import com.bloodlink.pledge.service.NotThePledgingDonorException;
import com.bloodlink.pledge.service.PledgeNotAcceptedException;
import com.bloodlink.pledge.service.PledgeNotFoundException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns pledge failures into RFC 7807 problem responses.
 *
 * <p>Every refusal here gets its own problem type rather than a shared
 * "conflict". A client showing a pledge button needs to react differently to
 * "you are not eligible until March" than to "somebody already has this" — and
 * a donor reading the message should learn what to do next, not merely that
 * something went wrong.
 */
@RestControllerAdvice
public class PledgeExceptionHandler {

    private static final String PROBLEM_BASE = "https://bloodlink.dev/problems/";

    @ExceptionHandler(PledgeNotFoundException.class)
    ProblemDetail onNotFound(PledgeNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage(), "pledge-not-found");
    }

    @ExceptionHandler(IllegalPledgeTransitionException.class)
    ProblemDetail onIllegalTransition(IllegalPledgeTransitionException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), "illegal-pledge-transition");
    }

    @ExceptionHandler(AlreadyPledgedException.class)
    ProblemDetail onAlreadyPledged(AlreadyPledgedException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), "already-pledged");
    }

    @ExceptionHandler(DonorProfileRequiredException.class)
    ProblemDetail onProfileRequired(DonorProfileRequiredException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), "donor-profile-required");
    }

    @ExceptionHandler(IncompatibleBloodGroupException.class)
    ProblemDetail onIncompatible(IncompatibleBloodGroupException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), "incompatible-blood-group");
    }

    @ExceptionHandler(DonorNotEligibleException.class)
    ProblemDetail onNotEligible(DonorNotEligibleException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), "donor-not-eligible");
    }

    @ExceptionHandler(NotThePledgingDonorException.class)
    ProblemDetail onNotThePledgingDonor(NotThePledgingDonorException exception) {
        return problem(HttpStatus.FORBIDDEN, exception.getMessage(), "not-the-pledging-donor");
    }

    /**
     * Somebody who is neither party asking for contact details.
     *
     * <p>403 rather than 404: the pledge exists, and pretending otherwise would
     * be theatre when a requester can already see every pledge on their own
     * request. What is hidden is the phone number, which is the thing that
     * matters.
     *
     * @param exception the refusal
     * @return 403
     */
    @ExceptionHandler(NotAPartyException.class)
    ProblemDetail onNotAParty(NotAPartyException exception) {
        return problem(HttpStatus.FORBIDDEN, exception.getMessage(), "not-a-party-to-this-pledge");
    }

    @ExceptionHandler(PledgeNotAcceptedException.class)
    ProblemDetail onNotAccepted(PledgeNotAcceptedException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), "pledge-not-accepted");
    }

    private ProblemDetail problem(HttpStatus status, String detail, String slug) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create(PROBLEM_BASE + slug));
        return problem;
    }
}
