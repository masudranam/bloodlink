package com.roktolink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the RoktoLink backend.
 *
 * <p>Skeleton only. Domain packages are introduced one merged spec at a time,
 * see /specs and docs/BACKLOG.md.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RoktoLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoktoLinkApplication.class, args);
    }
}
