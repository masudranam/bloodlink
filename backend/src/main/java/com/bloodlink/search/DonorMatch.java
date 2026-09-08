package com.bloodlink.search;

import com.bloodlink.donor.BloodGroup;
import com.bloodlink.donor.ThanaSummary;
import java.time.LocalDate;

/**
 * A donor who could serve one blood request.
 *
 * <p>What is missing matters more than what is here. No phone number: the
 * requester has no relationship with this person yet, and the number is revealed
 * only after a pledge is accepted (SPEC-008), where the reveal is audited. No
 * user id, so a donor cannot be correlated across endpoints. No coordinates —
 * {@code distanceKm} is the one scalar derived from them.
 *
 * <p>No {@code isEligible} either: every donor in a search result is eligible by
 * construction, so the field would always be true, and publishing it would
 * invite a client to filter on it as though it might not be.
 *
 * @param bloodGroup       what this donor can give, which is compatible with the
 *                         patient but not necessarily the same group
 * @param distanceKm       kilometres from the hospital to the donor's thana
 *                         centroid, rounded to one decimal because the centroid
 *                         is only accurate to about a kilometre and more digits
 *                         would be false precision
 * @param lastDonationDate when they last gave blood, or null if never
 * @param nextEligibleDate the first date they may give again, in the past for
 *                         every donor here, and null for one who has never given
 */
public record DonorMatch(
        Long donorId,
        String fullName,
        BloodGroup bloodGroup,
        ThanaSummary thana,
        double distanceKm,
        LocalDate lastDonationDate,
        LocalDate nextEligibleDate) {
}
