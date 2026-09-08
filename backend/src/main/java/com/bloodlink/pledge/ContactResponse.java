package com.bloodlink.pledge;

import java.time.Instant;

/**
 * The reveal.
 *
 * Symmetrical: the requester sees the donor, the donor sees the requester.
 * revealedAt is the audit row's own timestamp, echoed back so the caller can see
 * that the look was recorded rather than being told it was.
 *
 * @param pledgeId     the pledge that entitled them to look
 * @param requestId    the request behind it
 * @param counterparty the other party, with their number
 * @param revealedAt   when this reveal was written to the log
 */
public record ContactResponse(
        Long pledgeId,
        Long requestId,
        CounterpartySummary counterparty,
        Instant revealedAt) {
}
