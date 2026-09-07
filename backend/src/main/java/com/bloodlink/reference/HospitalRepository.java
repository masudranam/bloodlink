package com.bloodlink.reference;

import org.springframework.data.jpa.repository.JpaRepository;

/** Lookups over the seeded hospital reference data. */
public interface HospitalRepository extends JpaRepository<Hospital, Long> {
}
