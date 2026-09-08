package com.bloodlink.logging;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the scheduler that runs the expiry job.
 *
 * Separate from the application class so that the one annotation which makes
 * background work possible in this project is somewhere a reader can find it,
 * next to the logging that background work produces.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
