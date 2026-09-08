package com.bloodlink.auth;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the bearer scheme so Swagger UI gets an Authorize button: log in
 * through {@code /api/auth/login}, paste the token once, and every protected
 * endpoint is callable from the browser.
 *
 * <p>SPEC-010 owns the rest of the OpenAPI polish — grouping, examples, the
 * exported document.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    OpenAPI bloodlinkOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("BloodLink API")
                        .version("v1")
                        .description("""
                                A privacy-first blood donor network for Bangladesh.

                                **Three rules this API exists to enforce.**

                                1. **A phone number appears in exactly one response.** \
                                `GET /api/pledges/{id}/contact`, for an ACCEPTED pledge, to the \
                                two people in it. Every call appends a row to an append-only \
                                reveal log, readable by the person whose number it is at \
                                `GET /api/me/reveals`. No list or page response anywhere in this \
                                document declares a phone property.
                                2. **Eligibility is computed, never stored.** `isEligible` and \
                                `nextEligibleDate` are derived from `lastDonationDate` and a \
                                configured interval on every read. There is no `is_eligible` \
                                column, and there never will be.
                                3. **Compatibility is a pure function.** One dependency-free \
                                service holds the 8x8 matrix, and both donor search and pledging \
                                ask it rather than restating it.

                                Sign in with `POST /api/auth/login`, then paste the \
                                `accessToken` into **Authorize** above.
                                """))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the accessToken from POST /api/auth/login")));
    }
}
