package com.roktolink.auth;

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
    OpenAPI roktolinkOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RoktoLink API")
                        .version("v0")
                        .description("A privacy-first blood donor network for Bangladesh. "
                                + "No list response ever contains a phone number."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the accessToken from POST /api/auth/login")));
    }
}
