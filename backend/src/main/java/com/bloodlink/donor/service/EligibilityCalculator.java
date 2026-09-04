package com.bloodlink.donor.service;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Answers whether a donor may give blood, and when they next can.
 *
 * <p>This class is the whole of pillar 2. Nothing it returns is ever written
 * down: given a last donation date it recomputes the answer from the configured
 * interval and today's date, every single time it is asked. A stored
 * {@code is_eligible} flag would be correct on the day it was written and wrong
 * every day after.
 *
 * <p>The rule is {@code lastDonationDate + interval < today}, so the first day a
 * donor is eligible again is {@code lastDonationDate + interval + 1}. The
 * {@code + 1} is the strict inequality made explicit, and it is the easiest thing
 * in this project to get quietly wrong by a day.
 *
 * <p>It depends on a {@link Clock} and two numbers and nothing else — no
 * database, no request, no user — so the boundary can be tested without waiting
 * for midnight.
 */
@Component
public class EligibilityCalculator {

    private final Clock clock;
    private final EligibilityProperties properties;

    public EligibilityCalculator(Clock clock, EligibilityProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * Today, in the donor's zone rather than the server's.
     *
     * @return the current date in the configured zone
     */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(properties.zone()));
    }

    /**
     * The first date on which a donor may give again.
     *
     * @param lastDonationDate when the donor last gave blood, or null if never
     * @return that date, or null for a donor who has never recorded a donation —
     *         null rather than a date in the past, because there is no interval to
     *         count from
     */
    public LocalDate nextEligibleDate(LocalDate lastDonationDate) {
        if (lastDonationDate == null) {
            return null;
        }
        return lastDonationDate.plusDays(properties.donationIntervalDays() + 1L);
    }

    /**
     * Whether a donor may give blood today.
     *
     * @param lastDonationDate when the donor last gave blood, or null if never
     * @return true when the interval has fully elapsed, and always true for a
     *         donor who has never recorded a donation
     */
    public boolean isEligible(LocalDate lastDonationDate) {
        LocalDate next = nextEligibleDate(lastDonationDate);
        return next == null || !today().isBefore(next);
    }

    /**
     * The configured interval, echoed to clients so the answer is explicable
     * without hardcoding 90 anywhere else.
     *
     * @return days between permitted donations
     */
    public int donationIntervalDays() {
        return properties.donationIntervalDays();
    }
}
