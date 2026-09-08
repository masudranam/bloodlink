package com.bloodlink.search.service;

import com.bloodlink.donor.BloodGroup;
import com.bloodlink.donor.ThanaSummary;
import com.bloodlink.donor.service.BloodCompatibilityService;
import com.bloodlink.donor.service.EligibilityCalculator;
import com.bloodlink.reference.Hospital;
import com.bloodlink.request.BloodRequest;
import com.bloodlink.request.PageResponse;
import com.bloodlink.request.service.BloodRequestService;
import com.bloodlink.search.DonorMatch;
import com.bloodlink.search.DonorMatchRow;
import com.bloodlink.search.DonorSearchRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The donors who could serve one blood request.
 *
 * <p>This service decides nothing on its own. Compatibility comes from
 * {@link BloodCompatibilityService}, today and the interval come from
 * {@link EligibilityCalculator}, ownership and liveness come from
 * {@link BloodRequestService}, and the database applies them. What is left here
 * is wiring, and that is deliberate: three pillars meet in this one query, and
 * each of them should have exactly one implementation.
 */
@Service
public class DonorSearchService {

    private final BloodRequestService requests;
    private final DonorSearchRepository donors;
    private final BloodCompatibilityService compatibility;
    private final EligibilityCalculator eligibility;

    public DonorSearchService(BloodRequestService requests,
                              DonorSearchRepository donors,
                              BloodCompatibilityService compatibility,
                              EligibilityCalculator eligibility) {
        this.requests = requests;
        this.donors = donors;
        this.compatibility = compatibility;
        this.eligibility = eligibility;
    }

    /**
     * Searches for donors against a request the caller raised.
     *
     * @param requesterId the authenticated requester
     * @param requestId   the request to search against
     * @param radiusKm    how far from the hospital to look
     * @param page        zero-based page number
     * @param size        page size
     * @return a page of matches, nearest first, none of them carrying a phone
     *         number
     * @throws com.bloodlink.request.service.RequestNotFoundException  if no such
     *         request exists
     * @throws com.bloodlink.request.service.NotTheRequesterException  if somebody
     *         else raised it
     * @throws com.bloodlink.request.service.RequestNotActiveException if it has
     *         reached a terminal status
     */
    @Transactional(readOnly = true)
    public PageResponse<DonorMatch> search(long requesterId, long requestId, int radiusKm, int page, int size) {
        BloodRequest request = requests.requireActiveAndOwnedBy(requesterId, requestId);
        Hospital hospital = request.getHospital();

        // The matrix answers this, once, here. The query only filters on the answer.
        List<String> acceptableGroups = compatibility.compatibleDonorsFor(request.getPatientBloodGroup())
                .stream()
                .map(BloodGroup::getSymbol)
                .toList();

        Page<DonorMatchRow> rows = donors.search(
                acceptableGroups,
                eligibility.today(),
                eligibility.donationIntervalDays(),
                hospital.getLatitude().doubleValue(),
                hospital.getLongitude().doubleValue(),
                radiusKm,
                PageRequest.of(page, size));

        return PageResponse.of(rows.map(this::toMatch));
    }

    private DonorMatch toMatch(DonorMatchRow row) {
        return new DonorMatch(
                row.getDonorId(),
                row.getFullName(),
                BloodGroup.fromSymbol(row.getBloodGroup()),
                new ThanaSummary(row.getThanaId(), row.getThanaName(), row.getThanaDistrict()),
                roundToOneDecimal(row.getDistanceKm()),
                row.getLastDonationDate(),
                eligibility.nextEligibleDate(row.getLastDonationDate()));
    }

    /**
     * Rounds a distance for publication.
     *
     * <p>A thana centroid is accurate to roughly a kilometre, so publishing
     * {@code 1.4372} km would be false precision. The rounding happens here
     * rather than in SQL so that the radius filter and the sort both run against
     * the true value: rounding first would admit a donor at 10.04 km to a 10 km
     * search.
     *
     * @param kilometres the unrounded Haversine distance
     * @return the same distance to one decimal place
     */
    static double roundToOneDecimal(double kilometres) {
        return Math.round(kilometres * 10.0) / 10.0;
    }
}
