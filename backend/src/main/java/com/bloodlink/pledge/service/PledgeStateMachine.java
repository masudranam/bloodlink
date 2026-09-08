package com.bloodlink.pledge.service;

import static com.bloodlink.pledge.PledgeStatus.ACCEPTED;
import static com.bloodlink.pledge.PledgeStatus.DECLINED;
import static com.bloodlink.pledge.PledgeStatus.PENDING;
import static com.bloodlink.pledge.PledgeStatus.WITHDRAWN;

import com.bloodlink.pledge.PledgeStatus;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Which moves a pledge is allowed to make.
 *
 * <p>Shaped exactly like {@code BloodRequestStateMachine}, and for the same
 * reason: what matters is the twelve moves it refuses. A declined pledge must not
 * quietly become accepted, an accepted one must not be accepted twice, and a
 * withdrawn donor must not be counted as coming.
 *
 * <p>No dependencies — no repository, no clock, no user — so all 16 ordered pairs
 * are cheap to assert.
 *
 * <p>The one move worth defending is {@link PledgeStatus#ACCEPTED} to
 * {@link PledgeStatus#WITHDRAWN}. A donor who cannot come must be able to say so;
 * the alternative is a requester waiting for somebody who will not arrive, which
 * is worse than an awkward status change.
 */
@Service
public class PledgeStateMachine {

    private static final Map<PledgeStatus, Set<PledgeStatus>> ALLOWED;

    static {
        Map<PledgeStatus, Set<PledgeStatus>> table = new EnumMap<>(PledgeStatus.class);

        // The requester picks this donor or somebody else, or the donor pulls out.
        table.put(PENDING, EnumSet.of(ACCEPTED, DECLINED, WITHDRAWN));

        // Accepted is not a commitment the system can enforce on a person.
        table.put(ACCEPTED, EnumSet.of(WITHDRAWN));

        table.put(DECLINED, EnumSet.noneOf(PledgeStatus.class));
        table.put(WITHDRAWN, EnumSet.noneOf(PledgeStatus.class));

        Map<PledgeStatus, Set<PledgeStatus>> unmodifiable = new EnumMap<>(PledgeStatus.class);
        table.forEach((from, targets) -> unmodifiable.put(from, Collections.unmodifiableSet(targets)));
        ALLOWED = Collections.unmodifiableMap(unmodifiable);
    }

    /**
     * Whether a pledge may move between two statuses.
     *
     * <p>No status may move to itself: accepting an already accepted pledge means
     * the requester believed something that was not true, and reporting success
     * would hide that.
     *
     * @param from the current status
     * @param to   the status being asked for
     * @return true when the move is legal
     * @throws NullPointerException if either status is null
     */
    public boolean canTransition(PledgeStatus from, PledgeStatus to) {
        Objects.requireNonNull(from, "current status must not be null");
        Objects.requireNonNull(to, "target status must not be null");
        return ALLOWED.get(from).contains(to);
    }

    /**
     * Every status a pledge may move to from here.
     *
     * @param from the current status
     * @return an unmodifiable set, empty for a terminal status
     * @throws NullPointerException if the status is null
     */
    public Set<PledgeStatus> allowedFrom(PledgeStatus from) {
        Objects.requireNonNull(from, "current status must not be null");
        return ALLOWED.get(from);
    }

    /**
     * Asserts a move is legal, for callers about to perform it.
     *
     * @param from the current status
     * @param to   the status being asked for
     * @throws IllegalPledgeTransitionException if the move is refused
     */
    public void requireTransition(PledgeStatus from, PledgeStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalPledgeTransitionException(from, to);
        }
    }
}
