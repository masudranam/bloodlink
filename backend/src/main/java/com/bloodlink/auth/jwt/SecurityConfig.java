package com.bloodlink.auth.jwt;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The stateless filter chain.
 *
 * <p>No session, no cookie, no CSRF token: a request is authenticated by its
 * bearer token or not at all. That is what makes SPEC-003 AC-10 hold, and it is
 * what lets the React client in SPEC-009 talk to the API from anywhere.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** HMAC-SHA256. A symmetric key keeps deployment to one secret. */
    public static final MacAlgorithm JWS_ALGORITHM = MacAlgorithm.HS256;

    private static final int MINIMUM_SECRET_BYTES = 32;

    private static final String[] PUBLIC_GET_PATHS = {
        "/actuator/health",
        "/v3/api-docs",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/swagger-ui/**",
    };

    private final SecretKeySpec signingKey;

    public SecurityConfig(JwtProperties properties) {
        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "bloodlink.security.jwt.secret must be at least " + MINIMUM_SECRET_BYTES
                            + " bytes; set BLOODLINK_JWT_SECRET");
        }
        this.signingKey = new SecretKeySpec(secret, JWS_ALGORITHM.getName());
    }

    @Bean
    SecurityFilterChain apiFilterChain(HttpSecurity http,
                                       ProblemDetailEntryPoint entryPoint,
                                       ProblemDetailAccessDeniedHandler deniedHandler) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                        // The container's error dispatch. Without this, any
                        // exception that escapes a controller advice comes back as
                        // a misleading 401 instead of its real status.
                        .requestMatchers("/error").permitAll()
                        // SPEC-004: the first role gate in the project. A
                        // REQUESTER token is somebody, so this is a 403 and not
                        // a 401.
                        .requestMatchers("/api/donors/me/**", "/api/donors/me").hasRole("DONOR")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(signingKey).macAlgorithm(JWS_ALGORITHM).build();
    }

    /**
     * Turns the single {@code role} claim into a Spring authority. The converter
     * accepts a space-delimited string, so {@code "DONOR"} becomes
     * {@code ROLE_DONOR}.
     *
     * @return the configured converter
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
