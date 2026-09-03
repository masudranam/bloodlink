package com.roktolink.donor.service;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Eligibility configuration.
 *
 * @param donationIntervalDays days that must pass after a donation before the
 *                             donor may give again. Config-driven so it can be
 *                             corrected without a deployment, and so no answer
 *                             derived from it is ever worth storing.
 * @param zone                 the zone "today" is evaluated in. Eligibility is a
 *                             question about the donor's calendar day.
 */
@ConfigurationProperties(prefix = "roktolink.eligibility")
public record EligibilityProperties(int donationIntervalDays, ZoneId zone) {
}
