package com.bloodlink.search;

import java.time.LocalDate;

/**
 * One row of the donor search query.
 *
 * <p>A Spring Data interface projection: the accessor names correspond to the
 * quoted aliases in {@link DonorSearchRepository#SEARCH}, and nothing outside
 * that select list can be read through this type. That is the point of it. An
 * entity would carry the donor's {@code AppUser}, and from there their phone
 * number, one getter away from a response body.
 *
 * <p>There is deliberately no {@code getPhone()}, and SPEC-007 AC-14 asserts its
 * absence by reflection so that adding one is a test failure rather than a
 * quiet widening.
 */
public interface DonorMatchRow {

    Long getDonorId();

    String getFullName();

    /** The stored symbol, for example {@code O-}. */
    String getBloodGroup();

    Long getThanaId();

    String getThanaName();

    String getThanaDistrict();

    /** Null for a donor who has never recorded a donation. */
    LocalDate getLastDonationDate();

    /** Kilometres from the request's hospital, unrounded. */
    Double getDistanceKm();
}
