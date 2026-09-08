package com.bloodlink.pledge;

import java.time.Instant;

/**
 * One entry in the log of who has seen your number.
 *
 * Carries no phone number at all - not the viewer's, and not the caller's own.
 * Echoing back a number the caller already knows would put one into a paged list
 * response, which is the exact shape this project refuses.
 *
 * @param requestId  which request the reveal happened under
 * @param pledgeId   which pledge entitled the viewer to look
 * @param viewer     who looked, and in what role
 * @param revealedAt when
 */
public record RevealResponse(
        Long id,
        Long requestId,
        Long pledgeId,
        ViewerSummary viewer,
        Instant revealedAt) {
}
