package com.bloodlink.request.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Expiry configuration.
 *
 * @param enabled whether the job runs at all. Off means the bean is never
 *                created, so nothing is scheduled rather than scheduled and
 *                skipped.
 * @param cron    when to run, in Spring's six-field cron. Config-driven so the
 *                cadence can change without a deployment, and so a verification
 *                run can ask for every few seconds instead of waiting an hour.
 */
@ConfigurationProperties(prefix = "bloodlink.expiry")
public record ExpiryProperties(boolean enabled, String cron) {
}
