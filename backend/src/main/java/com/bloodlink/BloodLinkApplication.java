package com.bloodlink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the BloodLink backend.
 *
 * <p>Skeleton only. Domain packages are introduced one merged spec at a time,
 * see /specs and docs/BACKLOG.md.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BloodLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(BloodLinkApplication.class, args);
    }
}
