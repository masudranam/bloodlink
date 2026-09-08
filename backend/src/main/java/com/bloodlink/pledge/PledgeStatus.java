package com.bloodlink.pledge;

/**
 * Where a pledge is in its life.
 *
 * <p>PENDING is a donor having offered. ACCEPTED is the requester having picked
 * this donor, and it is the only status that permits a contact reveal. DECLINED
 * is the requester having picked somebody else, WITHDRAWN is the donor having
 * pulled out.
 *
 * <p>The last two are terminal. A donor who changes their mind again makes a new
 * pledge, so the trail of what was offered and what was answered stays intact.
 */
public enum PledgeStatus {

    PENDING,
    ACCEPTED,
    DECLINED,
    WITHDRAWN;

    /**
     * Whether this status has any legal move out of it.
     *
     * @return true for DECLINED and WITHDRAWN
     */
    public boolean isTerminal() {
        return this == DECLINED || this == WITHDRAWN;
    }

    /**
     * Whether a pledge in this status still counts towards a request being
     * pledged.
     *
     * <p>Both routes out of PLEDGED depend on this: when the last active pledge
     * on a request goes away, the request returns to OPEN and reappears in the
     * feed.
     *
     * @return true for PENDING and ACCEPTED
     */
    public boolean isActive() {
        return this == PENDING || this == ACCEPTED;
    }
}
