package com.bloodlink.request;

/**
 * Where a blood request is in its life.
 *
 * OPEN is asking, PLEDGED means at least one donor has offered, FULFILLED means
 * the requester confirmed blood was given. CANCELLED and EXPIRED are the two ways
 * a request stops mattering without being met: someone withdrew it, or the date
 * it was needed by went past.
 *
 * The last three are terminal. A new need is a new request, with its own trail.
 */
public enum BloodRequestStatus {

    OPEN,
    PLEDGED,
    FULFILLED,
    CANCELLED,
    EXPIRED;

    /**
     * Whether this status has any legal move out of it.
     *
     * @return true for FULFILLED, CANCELLED and EXPIRED
     */
    public boolean isTerminal() {
        return this == FULFILLED || this == CANCELLED || this == EXPIRED;
    }
}
