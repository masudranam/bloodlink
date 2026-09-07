package com.bloodlink.request;

import com.bloodlink.donor.BloodGroup;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * A request as submitted.
 *
 * There is no status field. A request is always created OPEN, and every later
 * change goes through the state machine, so there is no way for a client to
 * assert a lifecycle position it has not earned.
 *
 * @param patientBloodGroup one of the eight symbols
 * @param hospitalId        a seeded hospital
 * @param unitsNeeded       1 to 10
 * @param neededBy          the date after which this stops being useful
 * @param note              free text for donors, at most 500 characters
 */
public record CreateBloodRequest(

        @NotNull(message = "must be one of A+ A- B+ B- AB+ AB- O+ O-")
        BloodGroup patientBloodGroup,

        @NotNull(message = "must not be null")
        Long hospitalId,

        @NotNull(message = "must not be null")
        @Min(value = 1, message = "must be at least 1")
        @Max(value = 10, message = "must be at most 10")
        Integer unitsNeeded,

        @NotNull(message = "must not be null")
        LocalDate neededBy,

        @Size(max = 500, message = "must be at most 500 characters")
        String note) {
}
