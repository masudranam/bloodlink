package com.bloodlink.donor;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Lookups over donor profiles.
 *
 * There is at most one profile per user, enforced by the unique constraint on
 * donor_profile.user_id, so a lookup by user returns an Optional rather than a list.
 */
public interface DonorProfileRepository extends JpaRepository<DonorProfile, Long> {

    Optional<DonorProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
