package com.bloodlink.request.service;

import com.bloodlink.auth.service.InvalidCredentialsException;
import com.bloodlink.reference.Hospital;
import com.bloodlink.reference.HospitalRepository;
import com.bloodlink.request.BloodRequest;
import com.bloodlink.request.BloodRequestRepository;
import com.bloodlink.request.BloodRequestResponse;
import com.bloodlink.request.BloodRequestStatus;
import com.bloodlink.request.CreateBloodRequest;
import com.bloodlink.request.HospitalSummary;
import com.bloodlink.request.PageResponse;
import com.bloodlink.request.RequesterSummary;
import com.bloodlink.user.AppUser;
import com.bloodlink.user.AppUserRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Raising requests, reading them, and moving them through their lifecycle.
 *
 * <p>Every status change in this class goes through
 * {@link BloodRequestStateMachine} first. Nothing here decides for itself whether
 * a move is allowed, which is what stops SPEC-008 and SPEC-010 growing their own
 * slightly different rules later.
 */
@Service
public class BloodRequestService {

    private final BloodRequestRepository requests;
    private final AppUserRepository users;
    private final HospitalRepository hospitals;
    private final BloodRequestStateMachine stateMachine;
    private final Clock clock;

    public BloodRequestService(BloodRequestRepository requests,
                               AppUserRepository users,
                               HospitalRepository hospitals,
                               BloodRequestStateMachine stateMachine,
                               Clock clock) {
        this.requests = requests;
        this.users = users;
        this.hospitals = hospitals;
        this.stateMachine = stateMachine;
        this.clock = clock;
    }

    /**
     * Raises a request. It starts OPEN and can start no other way.
     *
     * @param requesterId the authenticated requester
     * @param body        the submitted request
     * @return the created request
     * @throws InvalidRequestException for an unknown hospital or a past date
     */
    @Transactional
    public BloodRequestResponse create(long requesterId, CreateBloodRequest body) {
        // A valid token whose account has been deleted is not an identity. Same
        // reasoning as AuthService.me, and the same 401.
        AppUser requester = users.findById(requesterId)
                .orElseThrow(InvalidCredentialsException::new);

        Hospital hospital = hospitals.findById(body.hospitalId())
                .orElseThrow(() -> new InvalidRequestException(
                        "hospitalId", "no such hospital: " + body.hospitalId()));

        LocalDate today = LocalDate.now(clock);
        if (body.neededBy().isBefore(today)) {
            throw new InvalidRequestException("neededBy", "must not be in the past");
        }

        BloodRequest request = new BloodRequest(
                requester,
                body.patientBloodGroup(),
                hospital,
                body.unitsNeeded().shortValue(),
                body.neededBy(),
                body.note());

        return toResponse(requests.save(request));
    }

    /**
     * The feed.
     *
     * @param status which lifecycle position to list
     * @param page   zero-based page number
     * @param size   page size
     * @return a page of requests, newest first
     */
    @Transactional(readOnly = true)
    public PageResponse<BloodRequestResponse> feed(BloodRequestStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<BloodRequestResponse> found = requests.findByStatus(status, pageRequest).map(this::toResponse);
        return PageResponse.of(found);
    }

    /**
     * One request, by id.
     *
     * @param id the request id
     * @return the request
     * @throws RequestNotFoundException if there is no such request
     */
    @Transactional(readOnly = true)
    public BloodRequestResponse get(long id) {
        return toResponse(require(id));
    }

    /**
     * Cancels a request the caller raised.
     *
     * @param requesterId the authenticated requester
     * @param id          the request id
     * @return the updated request
     * @throws NotTheRequesterException    if somebody else raised it
     * @throws IllegalTransitionException  if it cannot be cancelled from here
     */
    @Transactional
    public BloodRequestResponse cancel(long requesterId, long id) {
        return transitionOwn(requesterId, id, BloodRequestStatus.CANCELLED);
    }

    /**
     * Confirms that blood was given.
     *
     * <p>Only legal from PLEDGED: with nobody having offered, there is nothing to
     * confirm, and allowing it would let a request be closed without a donor ever
     * being involved.
     *
     * @param requesterId the authenticated requester
     * @param id          the request id
     * @return the updated request
     * @throws NotTheRequesterException    if somebody else raised it
     * @throws IllegalTransitionException  if it is not currently PLEDGED
     */
    @Transactional
    public BloodRequestResponse fulfil(long requesterId, long id) {
        return transitionOwn(requesterId, id, BloodRequestStatus.FULFILLED);
    }

    /**
     * Moves a request without an ownership check, for callers that are not the
     * requester: a donor pledging in SPEC-008, and the expiry job in SPEC-010.
     *
     * <p>The state machine still decides. This is a door for other services, not
     * a way round the guards.
     *
     * @param id     the request id
     * @param target the status to move to
     * @return the updated request
     * @throws IllegalTransitionException if the move is refused
     */
    @Transactional
    public BloodRequestResponse transition(long id, BloodRequestStatus target) {
        BloodRequest request = require(id);
        stateMachine.requireTransition(request.getStatus(), target);
        request.applyStatus(target);
        return toResponse(requests.save(request));
    }

    /**
     * The request behind a donor search: it must exist, belong to the caller, and
     * still be live.
     *
     * <p>Returns the entity rather than a response, because the caller
     * (SPEC-007) needs the hospital's coordinates and the patient's group, and
     * neither belongs in a client-facing DTO. It lives here so that ownership is
     * decided in one place: a search that grew its own check would be free to
     * disagree with cancel and fulfil.
     *
     * <p>Ownership is checked before liveness, the same order as
     * {@link #transitionOwn}: a stranger gets 403 whatever state the request is
     * in, so an error cannot tell them what state somebody else's request is in.
     *
     * @param requesterId the authenticated requester
     * @param id          the request id
     * @return the live request they raised
     * @throws RequestNotFoundException  if no such request exists
     * @throws NotTheRequesterException  if somebody else raised it
     * @throws RequestNotActiveException if it has reached a terminal status
     */
    @Transactional(readOnly = true)
    public BloodRequest requireActiveAndOwnedBy(long requesterId, long id) {
        BloodRequest request = require(id);
        if (!request.getRequester().getId().equals(requesterId)) {
            throw new NotTheRequesterException();
        }
        if (request.getStatus().isTerminal()) {
            throw new RequestNotActiveException(request.getStatus());
        }
        return request;
    }

    private BloodRequestResponse transitionOwn(long requesterId, long id, BloodRequestStatus target) {
        BloodRequest request = require(id);
        if (!request.getRequester().getId().equals(requesterId)) {
            throw new NotTheRequesterException();
        }
        stateMachine.requireTransition(request.getStatus(), target);
        request.applyStatus(target);
        return toResponse(requests.save(request));
    }

    private BloodRequest require(long id) {
        return requests.findById(id).orElseThrow(() -> new RequestNotFoundException(id));
    }

    private BloodRequestResponse toResponse(BloodRequest request) {
        Hospital hospital = request.getHospital();
        AppUser requester = request.getRequester();

        return new BloodRequestResponse(
                request.getId(),
                request.getPatientBloodGroup(),
                new HospitalSummary(hospital.getId(), hospital.getName(), hospital.getThana().getName()),
                request.getUnitsNeeded(),
                request.getNeededBy(),
                request.getStatus(),
                request.getNote(),
                new RequesterSummary(requester.getId(), requester.getFullName()),
                request.getCreatedAt());
    }
}
