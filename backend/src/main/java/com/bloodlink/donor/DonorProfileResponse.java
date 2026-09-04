package com.bloodlink.donor;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * A donor profile as returned.
 *
 * <p>{@code isEligible} and {@code nextEligibleDate} are computed on every read
 * and correspond to no column. {@code donationIntervalDays} is echoed so a client
 * can explain the answer to a donor without hardcoding 90, and so the arithmetic
 * is visible rather than magic.
 *
 * <p>No phone number, by SPEC-004 AC-12: a donor reading their own profile does
 * not need to be told their own number, and adding the field here would be the
 * first step towards it appearing somewhere it must not.
 *
 * @param id                   the profile id
 * @param bloodGroup           what this donor can give
 * @param thana                where they are, to thana granularity
 * @param lastDonationDate     when they last gave blood, or null if never
 * @param available            whether they are currently offering to donate
 * @param isEligible           computed: the interval has fully elapsed
 * @param nextEligibleDate     computed: the first date they may give again, null
 *                             for a donor who has never recorded a donation
 * @param donationIntervalDays the configured interval the two computed fields
 *                             were derived from
 */
public record DonorProfileResponse(
        Long id,
        BloodGroup bloodGroup,
        ThanaSummary thana,
        LocalDate lastDonationDate,
        boolean available,

        // Without this, Jackson would publish the field as "eligible".
        @JsonProperty("isEligible")
        boolean isEligible,

        LocalDate nextEligibleDate,
        int donationIntervalDays) {
}
