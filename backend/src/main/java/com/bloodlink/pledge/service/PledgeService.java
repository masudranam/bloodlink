package com.bloodlink.pledge.service;

import com.bloodlink.donor.BloodGroup;
import com.bloodlink.donor.DonorProfile;
import com.bloodlink.donor.DonorProfileRepository;
import com.bloodlink.donor.ThanaSummary;
import com.bloodlink.donor.service.BloodCompatibilityService;
import com.bloodlink.donor.service.EligibilityCalculator;
import com.bloodlink.pledge.Pledge;
import com.bloodlink.pledge.PledgeDonorSummary;
import com.bloodlink.pledge.PledgeRepository;
import com.bloodlink.pledge.PledgeResponse;
import com.bloodlink.pledge.PledgeStatus;
import com.bloodlink.reference.Hospital;
import com.bloodlink.reference.Thana;
import com.bloodlink.request.BloodRequest;
import com.bloodlink.request.BloodRequestStatus;
import com.bloodlink.request.HospitalSummary;
import com.bloodlink.request.PageResponse;
import com.bloodlink.request.service.BloodRequestService;
import com.bloodlink.request.service.NotTheRequesterException;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pledges: making one, answering one, and withdrawing one.
 *
 * <p>Nothing in this class returns a phone number or can be asked for one. The
 * reveal lives in {@link ContactRevealService}, on its own, because it is the
 * only operation in BloodLink that hands out a number and the only one that must
 * write an audit row for doing so. Keeping the two apart means the privacy rule
 * has one implementation, in one file, rather than a condition inside a method
 * that also does five other things.
 *
 * <p>The three checks a pledge must pass — profile, compatibility, eligibility —
 * are all delegated. This service asks {@code BloodCompatibilityService} and
 * {@code EligibilityCalculator}; it does not decide either question, which is
 * what stops a second, slightly different copy of a pillar appearing here.
 */
@Service
public class PledgeService {

    private static final Logger LOG = LoggerFactory.getLogger(PledgeService.class);

    /** The statuses that keep a request in PLEDGED. */
    private static final Set<PledgeStatus> ACTIVE = EnumSet.of(PledgeStatus.PENDING, PledgeStatus.ACCEPTED);

    private final PledgeRepository pledges;
    private final DonorProfileRepository profiles;
    private final BloodRequestService requests;
    private final PledgeStateMachine stateMachine;
    private final BloodCompatibilityService compatibility;
    private final EligibilityCalculator eligibility;

    public PledgeService(PledgeRepository pledges,
                         DonorProfileRepository profiles,
                         BloodRequestService requests,
                         PledgeStateMachine stateMachine,
                         BloodCompatibilityService compatibility,
                         EligibilityCalculator eligibility) {
        this.pledges = pledges;
        this.profiles = profiles;
        this.requests = requests;
        this.stateMachine = stateMachine;
        this.compatibility = compatibility;
        this.eligibility = eligibility;
    }

    /**
     * A donor offers blood for a request.
     *
     * <p>An unavailable donor may still pledge. Availability governs whether
     * search offers them up; pledging is them volunteering anyway, and refusing
     * it would be the system overruling a person about their own willingness.
     *
     * @param donorUserId the authenticated donor
     * @param requestId   the request being pledged against
     * @return the new PENDING pledge
     * @throws DonorProfileRequiredException     if the donor has no profile
     * @throws IncompatibleBloodGroupException   if the patient cannot receive
     *                                           from them
     * @throws DonorNotEligibleException         if the interval has not elapsed
     * @throws AlreadyPledgedException           if they have already pledged here
     */
    @Transactional
    public PledgeResponse pledge(long donorUserId, long requestId) {
        // Liveness and existence come from the request's own service. A pledge
        // against a cancelled request is refused there, with the same message a
        // search against one gets.
        BloodRequest request = requests.requireActive(requestId);
        DonorProfile donor = profiles.findByUserId(donorUserId)
                .orElseThrow(DonorProfileRequiredException::new);

        BloodGroup patientGroup = request.getPatientBloodGroup();
        if (!compatibility.canReceive(patientGroup, donor.getBloodGroup())) {
            throw new IncompatibleBloodGroupException(patientGroup, donor.getBloodGroup());
        }
        if (!eligibility.isEligible(donor.getLastDonationDate())) {
            throw new DonorNotEligibleException(eligibility.nextEligibleDate(donor.getLastDonationDate()));
        }
        if (pledges.existsByRequestIdAndDonorId(requestId, donor.getId())) {
            throw new AlreadyPledgedException(requestId);
        }

        // saveAndFlush, so the unique constraint speaks now rather than at commit.
        // The check above loses a race between two concurrent pledges — both see
        // no existing row — and the index is what actually refuses the second.
        // Translating it here means the loser sees the same 409 as the checked
        // path instead of a 500 about a constraint they have never heard of.
        Pledge pledge;
        try {
            pledge = pledges.saveAndFlush(new Pledge(request, donor));
        } catch (DataIntegrityViolationException race) {
            throw new AlreadyPledgedException(requestId);
        }

        // Somebody has now offered, so the request is no longer merely open. The
        // move goes through the request's own state machine rather than being set
        // here.
        if (request.getStatus() == BloodRequestStatus.OPEN) {
            requests.transition(requestId, BloodRequestStatus.PLEDGED);
        }

        // Symbols, not constant names: a log that says O_NEGATIVE while the API
        // says O- makes an operator translate between two vocabularies.
        LOG.info("event=pledge_made pledgeId={} requestId={} donorId={} patientGroup={} donorGroup={}",
                pledge.getId(), requestId, donor.getId(),
                patientGroup.getSymbol(), donor.getBloodGroup().getSymbol());

        return toResponse(pledge);
    }

    /**
     * The pledges on a request, for the requester who raised it.
     *
     * @param requesterId the authenticated requester
     * @param requestId   their request
     * @param page        zero-based page number
     * @param size        page size
     * @return a page of pledges, newest first, carrying no phone numbers
     * @throws NotTheRequesterException if somebody else raised it
     */
    @Transactional(readOnly = true)
    public PageResponse<PledgeResponse> forRequest(long requesterId, long requestId, int page, int size) {
        requests.requireOwnedBy(requesterId, requestId);
        Page<Pledge> found = pledges.findByRequestIdOrderByCreatedAtDesc(requestId, PageRequest.of(page, size));
        return PageResponse.of(found.map(this::toResponse));
    }

    /**
     * A donor's own pledges, across every request they have offered on.
     *
     * @param donorUserId the authenticated donor
     * @param page        zero-based page number
     * @param size        page size
     * @return a page of their pledges, newest first
     * @throws DonorProfileRequiredException if they have no profile
     */
    @Transactional(readOnly = true)
    public PageResponse<PledgeResponse> forDonor(long donorUserId, int page, int size) {
        DonorProfile donor = profiles.findByUserId(donorUserId)
                .orElseThrow(DonorProfileRequiredException::new);
        Page<Pledge> found = pledges.findByDonorIdOrderByCreatedAtDesc(donor.getId(), PageRequest.of(page, size));
        return PageResponse.of(found.map(this::toResponse));
    }

    /**
     * The requester picks this donor.
     *
     * <p>The request stays PLEDGED. Accepting is not fulfilling: blood has not
     * been given yet, and SPEC-006 still owns that move.
     *
     * @param requesterId the authenticated requester
     * @param pledgeId    the pledge to accept
     * @return the accepted pledge
     */
    @Transactional
    public PledgeResponse accept(long requesterId, long pledgeId) {
        return decide(requesterId, pledgeId, PledgeStatus.ACCEPTED);
    }

    /**
     * The requester picks somebody else.
     *
     * @param requesterId the authenticated requester
     * @param pledgeId    the pledge to decline
     * @return the declined pledge
     */
    @Transactional
    public PledgeResponse decline(long requesterId, long pledgeId) {
        return decide(requesterId, pledgeId, PledgeStatus.DECLINED);
    }

    /**
     * The donor pulls out.
     *
     * <p>Legal from ACCEPTED as well as PENDING. A donor who cannot come must be
     * able to say so; the alternative is a requester waiting for somebody who
     * will not arrive.
     *
     * @param donorUserId the authenticated donor
     * @param pledgeId    their pledge
     * @return the withdrawn pledge
     * @throws NotThePledgingDonorException if it is not their pledge
     */
    @Transactional
    public PledgeResponse withdraw(long donorUserId, long pledgeId) {
        Pledge pledge = require(pledgeId);

        // Ownership before the transition, as everywhere else in this project: a
        // stranger gets 403 whatever state the pledge is in, so the error cannot
        // report on somebody else's arrangements.
        if (!pledge.getDonor().getUser().getId().equals(donorUserId)) {
            throw new NotThePledgingDonorException();
        }

        return apply(pledge, PledgeStatus.WITHDRAWN);
    }

    private PledgeResponse decide(long requesterId, long pledgeId, PledgeStatus target) {
        Pledge pledge = require(pledgeId);
        if (!pledge.getRequest().getRequester().getId().equals(requesterId)) {
            throw new NotTheRequesterException();
        }
        return apply(pledge, target);
    }

    private PledgeResponse apply(Pledge pledge, PledgeStatus target) {
        stateMachine.requireTransition(pledge.getStatus(), target);
        pledge.applyStatus(target);
        pledges.save(pledge);

        // After the save, so the count sees this pledge in its new status: a
        // withdrawal that is still PENDING in the database would count itself as
        // active and leave the request stranded in PLEDGED.
        returnRequestToOpenIfNothingActiveIsLeft(pledge.getRequest());

        LOG.info("event=pledge_answered pledgeId={} requestId={} status={} requestStatus={}",
                pledge.getId(), pledge.getRequest().getId(), target, pledge.getRequest().getStatus());

        return toResponse(pledge);
    }

    /**
     * Puts a request back in the feed when its last active pledge goes away.
     *
     * <p>Without this a request whose only pledge is declined or withdrawn sits
     * in PLEDGED forever: invisible to donors browsing an OPEN feed, and with
     * nobody coming.
     */
    private void returnRequestToOpenIfNothingActiveIsLeft(BloodRequest request) {
        if (request.getStatus() != BloodRequestStatus.PLEDGED) {
            return;
        }
        if (pledges.countByRequestIdAndStatusIn(request.getId(), ACTIVE) == 0) {
            requests.transition(request.getId(), BloodRequestStatus.OPEN);
        }
    }

    private Pledge require(long id) {
        return pledges.findById(id).orElseThrow(() -> new PledgeNotFoundException(id));
    }

    private PledgeResponse toResponse(Pledge pledge) {
        BloodRequest request = pledge.getRequest();
        Hospital hospital = request.getHospital();
        DonorProfile donor = pledge.getDonor();
        Thana thana = donor.getThana();

        return new PledgeResponse(
                pledge.getId(),
                request.getId(),
                request.getPatientBloodGroup(),
                new HospitalSummary(hospital.getId(), hospital.getName(), hospital.getThana().getName()),
                new PledgeDonorSummary(
                        donor.getId(),
                        donor.getUser().getFullName(),
                        donor.getBloodGroup(),
                        new ThanaSummary(thana.getId(), thana.getName(), thana.getDistrict())),
                pledge.getStatus(),
                pledge.getCreatedAt(),
                pledge.getDecidedAt());
    }
}
