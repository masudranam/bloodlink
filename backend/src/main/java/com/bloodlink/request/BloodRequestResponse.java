package com.bloodlink.request;

import com.bloodlink.donor.BloodGroup;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A blood request as returned, in the feed and on its own.
 *
 * This is a list response, so the privacy rule applies at its strictest: there is
 * no phone field here to leak, not a nulled one. The requester is a name and an
 * id. Their number reaches a donor only after a pledge is accepted, in SPEC-008,
 * and the reveal is audited when it happens.
 *
 * @param id                the request id
 * @param patientBloodGroup what the patient needs
 * @param hospital          where to come
 * @param unitsNeeded       how many units
 * @param neededBy          the date after which this stops being useful
 * @param status            where this request is in its life
 * @param note              free text from the requester, or null
 * @param requester         who is asking, without their phone number
 * @param createdAt         when it was raised
 */
public record BloodRequestResponse(
        Long id,
        BloodGroup patientBloodGroup,
        HospitalSummary hospital,
        int unitsNeeded,
        LocalDate neededBy,
        BloodRequestStatus status,
        String note,
        RequesterSummary requester,
        Instant createdAt) {
}
