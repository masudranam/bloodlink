package com.bloodlink.donor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * A donor profile as submitted, for both create and replace.
 *
 * Note what is absent: no eligibility field of any kind. Eligibility is derived
 * on read and is not something a client gets to assert. Nor is there any
 * latitude or longitude - a donor's location is their thana.
 *
 * @param bloodGroup       one of the eight symbols, for example B+
 * @param thanaId          a seeded thana
 * @param lastDonationDate when they last gave blood; null or omitted means never
 * @param available        whether they are offering to donate; omitted means true
 */
public record DonorProfileRequest(

        @NotNull(message = "must be one of A+ A- B+ B- AB+ AB- O+ O-")
        BloodGroup bloodGroup,

        @NotNull(message = "must not be null")
        Long thanaId,

        LocalDate lastDonationDate,

        Boolean available) {

    /**
     * Availability with its default applied.
     *
     * @return the submitted value, or true when omitted
     */
    public boolean availableOrDefault() {
        return available == null || available;
    }
}
