package com.bloodlink.donor.service;

import com.bloodlink.donor.DonorProfile;
import com.bloodlink.donor.DonorProfileRepository;
import com.bloodlink.donor.DonorProfileRequest;
import com.bloodlink.donor.DonorProfileResponse;
import com.bloodlink.donor.ThanaSummary;
import com.bloodlink.reference.Thana;
import com.bloodlink.reference.ThanaRepository;
import com.bloodlink.user.AppUser;
import com.bloodlink.user.AppUserRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A donor's own profile: create, read, replace, delete.
 *
 * <p>Every read runs the profile back through {@link EligibilityCalculator}, so
 * the eligibility a client sees is computed at the moment it is asked for. There
 * is no path through this class that writes an eligibility answer down.
 */
@Service
public class DonorProfileService {

    private final DonorProfileRepository profiles;
    private final AppUserRepository users;
    private final ThanaRepository thanas;
    private final EligibilityCalculator eligibility;

    public DonorProfileService(DonorProfileRepository profiles,
                               AppUserRepository users,
                               ThanaRepository thanas,
                               EligibilityCalculator eligibility) {
        this.profiles = profiles;
        this.users = users;
        this.thanas = thanas;
        this.eligibility = eligibility;
    }

    /**
     * Creates the caller's donor profile.
     *
     * @param userId  the authenticated donor
     * @param request the submitted profile
     * @return the created profile with eligibility computed
     * @throws ProfileAlreadyExistsException if this donor already has one
     * @throws InvalidProfileException       for an unknown thana or a future date
     */
    @Transactional
    public DonorProfileResponse create(long userId, DonorProfileRequest request) {
        if (profiles.existsByUserId(userId)) {
            throw new ProfileAlreadyExistsException("This donor already has a profile");
        }

        AppUser user = users.findById(userId)
                .orElseThrow(() -> new ProfileNotFoundException("No such user"));

        DonorProfile profile = new DonorProfile(
                user,
                request.bloodGroup(),
                resolveThana(request.thanaId()),
                validateDonationDate(request.lastDonationDate()),
                request.availableOrDefault());

        return toResponse(profiles.save(profile));
    }

    /**
     * Reads the caller's donor profile.
     *
     * @param userId the authenticated donor
     * @return the profile with eligibility computed fresh
     * @throws ProfileNotFoundException if they have not created one
     */
    @Transactional(readOnly = true)
    public DonorProfileResponse get(long userId) {
        return toResponse(require(userId));
    }

    /**
     * Replaces the caller's donor profile in full.
     *
     * <p>A replacement, not a merge: an omitted {@code lastDonationDate} clears
     * it and an omitted {@code available} resets it to true. That is what makes
     * clearing a nullable date expressible at all without JSON Patch.
     *
     * @param userId  the authenticated donor
     * @param request the replacement profile
     * @return the updated profile with eligibility recomputed
     * @throws ProfileNotFoundException if they have not created one
     * @throws InvalidProfileException  for an unknown thana or a future date
     */
    @Transactional
    public DonorProfileResponse replace(long userId, DonorProfileRequest request) {
        DonorProfile profile = require(userId);

        profile.setBloodGroup(request.bloodGroup());
        profile.setThana(resolveThana(request.thanaId()));
        profile.setLastDonationDate(validateDonationDate(request.lastDonationDate()));
        profile.setAvailable(request.availableOrDefault());

        return toResponse(profiles.save(profile));
    }

    /**
     * Deletes the caller's donor profile, leaving their account alone.
     *
     * @param userId the authenticated donor
     * @throws ProfileNotFoundException if they have not created one
     */
    @Transactional
    public void delete(long userId) {
        profiles.delete(require(userId));
    }

    private DonorProfile require(long userId) {
        return profiles.findByUserId(userId)
                .orElseThrow(() -> new ProfileNotFoundException("No donor profile for this user"));
    }

    private Thana resolveThana(Long thanaId) {
        return thanas.findById(thanaId)
                .orElseThrow(() -> new InvalidProfileException("thanaId", "no such thana: " + thanaId));
    }

    /**
     * Rejects a donation date in the future.
     *
     * <p>Checked here rather than with {@code @PastOrPresent} because "today" has
     * to mean today in the donor's zone. A database check constraint is not an
     * option either: Postgres refuses non-immutable functions in a CHECK, and a
     * row valid when written would otherwise rot as the clock moves.
     *
     * @param lastDonationDate the submitted date, possibly null
     * @return the same date
     * @throws InvalidProfileException if it is after today in the donor's zone
     */
    private LocalDate validateDonationDate(LocalDate lastDonationDate) {
        if (lastDonationDate != null && lastDonationDate.isAfter(eligibility.today())) {
            throw new InvalidProfileException("lastDonationDate", "must not be in the future");
        }
        return lastDonationDate;
    }

    private DonorProfileResponse toResponse(DonorProfile profile) {
        LocalDate lastDonation = profile.getLastDonationDate();
        Thana thana = profile.getThana();

        return new DonorProfileResponse(
                profile.getId(),
                profile.getBloodGroup(),
                new ThanaSummary(thana.getId(), thana.getName(), thana.getDistrict()),
                lastDonation,
                profile.isAvailable(),
                eligibility.isEligible(lastDonation),
                eligibility.nextEligibleDate(lastDonation),
                eligibility.donationIntervalDays());
    }
}
