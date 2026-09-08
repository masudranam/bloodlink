package com.bloodlink.reference;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Lookups over the seeded hospital reference data. */
public interface HospitalRepository extends JpaRepository<Hospital, Long> {

    /**
     * Every hospital with its thana already loaded.
     *
     * <p>The join fetch is load-bearing, not an optimisation. {@code thana} is a
     * lazy association and {@code open-in-view} is off, so reading
     * {@code getThana().getName()} after the repository call returns would throw
     * {@code LazyInitializationException} — which is exactly what it did the
     * first time the reference endpoint was exercised.
     *
     * <p>Fetching here rather than annotating the controller with
     * {@code @Transactional} keeps the transaction where the data access is, and
     * makes it one query instead of five.
     *
     * @return all hospitals, alphabetically, thanas initialised
     */
    @Query("select h from Hospital h join fetch h.thana order by h.name")
    List<Hospital> findAllWithThana();
}
