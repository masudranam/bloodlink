package com.bloodlink.pledge;

import com.bloodlink.donor.BloodGroup;
import com.bloodlink.donor.ThanaSummary;

/**
 * The donor behind a pledge, as shown to a requester.
 *
 * No phone number. A requester reading ten pledges has ten names and no numbers;
 * the number arrives only through the contact endpoint, and only for the one
 * pledge they accepted.
 *
 * @param donorId    the donor profile id
 * @param fullName   who they are
 * @param bloodGroup what they can give
 * @param thana      where they are, to thana granularity
 */
public record PledgeDonorSummary(
        Long donorId,
        String fullName,
        BloodGroup bloodGroup,
        ThanaSummary thana) {
}
