package com.bloodlink.pledge;

import com.bloodlink.donor.BloodGroup;
import com.bloodlink.request.HospitalSummary;
import java.time.Instant;

/**
 * A pledge as returned, to either side.
 *
 * One shape serves both audiences on purpose: a requester reading the pledges on
 * their request, and a donor reading their own pledges across requests. Two
 * shapes would mean two places to audit for a leaked phone number.
 *
 * @param requestId         the request pledged against
 * @param patientBloodGroup what the patient needs, so a donor can see why they
 *                          were eligible to offer
 * @param hospital          where to go
 * @param donor             who offered
 * @param status            PENDING, ACCEPTED, DECLINED or WITHDRAWN
 * @param pledgedAt         when the offer was made
 * @param decidedAt         when it was answered, or null while PENDING
 */
public record PledgeResponse(
        Long id,
        Long requestId,
        BloodGroup patientBloodGroup,
        HospitalSummary hospital,
        PledgeDonorSummary donor,
        PledgeStatus status,
        Instant pledgedAt,
        Instant decidedAt) {
}
