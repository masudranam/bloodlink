package com.bloodlink.request.service;

import static com.bloodlink.request.BloodRequestStatus.CANCELLED;
import static com.bloodlink.request.BloodRequestStatus.EXPIRED;
import static com.bloodlink.request.BloodRequestStatus.FULFILLED;
import static com.bloodlink.request.BloodRequestStatus.OPEN;
import static com.bloodlink.request.BloodRequestStatus.PLEDGED;

import com.bloodlink.request.BloodRequestStatus;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Which moves a blood request is allowed to make.
 *
 * <p>The value of a request having a lifecycle at all is in the moves it refuses:
 * a fulfilled request must not reopen for pledges, a cancelled one must not be
 * fulfilled, and an expired one must not accept a donor about to cross Dhaka for
 * nothing. So the table of legal moves is written out explicitly and asserted
 * over all 25 ordered pairs.
 *
 * <p>Like {@code BloodCompatibilityService}, this has no dependencies — no
 * repository, no clock, no user — which is what makes exhaustive testing cheap.
 *
 * <p>Two of the seven legal moves are not driven by any endpoint in SPEC-006.
 * {@link BloodRequestStatus#PLEDGED} is reached when a donor pledges (SPEC-008),
 * and {@link BloodRequestStatus#EXPIRED} by the scheduled job in SPEC-010. Their
 * guards live here regardless, so those specs cannot invent their own rules.
 */
@Service
public class BloodRequestStateMachine {

    private static final Map<BloodRequestStatus, Set<BloodRequestStatus>> ALLOWED;

    static {
        Map<BloodRequestStatus, Set<BloodRequestStatus>> table = new EnumMap<>(BloodRequestStatus.class);

        // Someone offered blood, the requester withdrew it, or the date passed.
        table.put(OPEN, EnumSet.of(PLEDGED, CANCELLED, EXPIRED));

        // PLEDGED -> OPEN matters: a request whose only pledge is withdrawn goes
        // back to the feed rather than being stranded with nobody coming.
        table.put(PLEDGED, EnumSet.of(OPEN, FULFILLED, CANCELLED, EXPIRED));

        table.put(FULFILLED, EnumSet.noneOf(BloodRequestStatus.class));
        table.put(CANCELLED, EnumSet.noneOf(BloodRequestStatus.class));
        table.put(EXPIRED, EnumSet.noneOf(BloodRequestStatus.class));

        Map<BloodRequestStatus, Set<BloodRequestStatus>> unmodifiable = new EnumMap<>(BloodRequestStatus.class);
        table.forEach((from, targets) -> unmodifiable.put(from, Collections.unmodifiableSet(targets)));
        ALLOWED = Collections.unmodifiableMap(unmodifiable);
    }

    /**
     * Whether a request may move between two statuses.
     *
     * <p>No status may move to itself. A double-submitted cancel is a conflict
     * rather than a silent success, because the second one means the caller
     * believed something that was not true.
     *
     * @param from the current status
     * @param to   the status being asked for
     * @return true when the move is legal
     * @throws NullPointerException if either status is null
     */
    public boolean canTransition(BloodRequestStatus from, BloodRequestStatus to) {
        Objects.requireNonNull(from, "current status must not be null");
        Objects.requireNonNull(to, "target status must not be null");
        return ALLOWED.get(from).contains(to);
    }

    /**
     * Every status a request may move to from here.
     *
     * @param from the current status
     * @return an unmodifiable set, empty for a terminal status
     * @throws NullPointerException if the status is null
     */
    public Set<BloodRequestStatus> allowedFrom(BloodRequestStatus from) {
        Objects.requireNonNull(from, "current status must not be null");
        return ALLOWED.get(from);
    }

    /**
     * Asserts a move is legal, for callers that are about to perform it.
     *
     * @param from the current status
     * @param to   the status being asked for
     * @throws IllegalTransitionException if the move is refused
     */
    public void requireTransition(BloodRequestStatus from, BloodRequestStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalTransitionException(from, to);
        }
    }
}
