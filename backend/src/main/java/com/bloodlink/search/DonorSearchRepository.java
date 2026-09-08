package com.bloodlink.search;

import com.bloodlink.donor.DonorProfile;
import java.time.LocalDate;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * The one query BloodLink exists for.
 *
 * <p>Four filters, applied together: the donor is available, their group is one
 * the patient can receive, the donation interval has fully elapsed, and their
 * thana centroid is within the radius of the hospital.
 *
 * <p>Two things are deliberately absent from the SQL below, and SPEC-007 AC-3,
 * AC-5 and AC-14 assert their absence against the query text itself:
 *
 * <ul>
 *   <li><strong>No hand-written group list.</strong> {@code :bloodGroups} arrives
 *       from {@code BloodCompatibilityService}. Writing
 *       {@code in ('B+','B-','O+','O-')} here would read as an optimisation and
 *       would silently diverge from the one tested copy of the matrix.</li>
 *   <li><strong>No {@code current_date}.</strong> Today is a bind parameter from
 *       the configured {@code Clock} in {@code Asia/Dhaka}. Asking the database
 *       for the date would move eligibility off that clock and into whatever zone
 *       the server happens to run in.</li>
 * </ul>
 *
 * <p>The distance is computed in the inner select and filtered in the outer one
 * so that the Haversine expression is written once. It is filtered and ordered
 * unrounded; rounding to one decimal happens on the way out, in
 * {@link com.bloodlink.search.service.DonorSearchService}, so that a donor at
 * 10.04 km is not admitted to a 10 km search by a rounding step.
 */
public interface DonorSearchRepository extends Repository<DonorProfile, Long> {

    /**
     * Every donor who passes all four filters, with an unrounded distance.
     *
     * <p>Shared verbatim by the page query and the count query: a count that
     * filtered differently from the page it counts is the classic way for
     * pagination to start lying.
     */
    String CANDIDATES = """
            select dp.id                 as "donorId",
                   u.full_name           as "fullName",
                   dp.blood_group        as "bloodGroup",
                   t.id                  as "thanaId",
                   t.name                as "thanaName",
                   t.district            as "thanaDistrict",
                   dp.last_donation_date as "lastDonationDate",
                   6371 * 2 * asin(sqrt(
                       power(sin(radians(cast(t.latitude as double precision)
                                         - cast(:lat as double precision)) / 2), 2)
                       + cos(radians(cast(:lat as double precision)))
                         * cos(radians(cast(t.latitude as double precision)))
                         * power(sin(radians(cast(t.longitude as double precision)
                                             - cast(:lng as double precision)) / 2), 2)
                   )) as "distanceKm"
            from donor_profile dp
            join app_user u on u.id = dp.user_id
            join thana t on t.id = dp.thana_id
            where dp.available = true
              and dp.blood_group in (:bloodGroups)
              and (dp.last_donation_date is null
                   or dp.last_donation_date + cast(:intervalDays as integer) < cast(:today as date))
            """;

    /**
     * The page. Three sort keys, and all three are load-bearing.
     *
     * <p>Distance first, which is what a requester asked for. Then
     * {@code lastDonationDate} ascending with nulls first, because a donor's
     * location is their thana centroid and so everyone in one thana ties on
     * distance exactly — this breaks the tie towards whoever has rested longest,
     * which also spreads the asking around. Then the id, so that the order is
     * total: without it Postgres may return tied rows differently on each page,
     * repeating some donors and hiding others.
     */
    String SEARCH = """
            select m.*
            from (
            """ + CANDIDATES + """
            ) m
            where m."distanceKm" <= cast(:radiusKm as double precision)
            order by m."distanceKm", m."lastDonationDate" asc nulls first, m."donorId"
            """;

    /** The same four filters and the same radius, counted. */
    String SEARCH_COUNT = """
            select count(*)
            from (
            """ + CANDIDATES + """
            ) m
            where m."distanceKm" <= cast(:radiusKm as double precision)
            """;

    /**
     * Finds the donors who could serve one request.
     *
     * @param bloodGroups  the stored symbols of every group the patient may
     *                     receive from, supplied by the compatibility matrix and
     *                     never written out here
     * @param today        the current date in the configured zone
     * @param intervalDays days that must have fully elapsed since a donation
     * @param lat          the hospital's latitude
     * @param lng          the hospital's longitude
     * @param radiusKm     how far from the hospital to look
     * @param pageable     the page to return; the sort lives in the query, so
     *                     this supplies only the limit and offset
     * @return a page of matches, nearest first
     */
    @Query(value = SEARCH, countQuery = SEARCH_COUNT, nativeQuery = true)
    Page<DonorMatchRow> search(@Param("bloodGroups") Collection<String> bloodGroups,
                               @Param("today") LocalDate today,
                               @Param("intervalDays") int intervalDays,
                               @Param("lat") double lat,
                               @Param("lng") double lng,
                               @Param("radiusKm") int radiusKm,
                               Pageable pageable);
}
