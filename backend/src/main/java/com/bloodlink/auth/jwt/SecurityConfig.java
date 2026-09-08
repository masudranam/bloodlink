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
                        // SPEC-006. Raising a request and moving one through its
                        // lifecycle are a requester's job; reading the feed is
                        // open to both roles, because a donor browsing it is the
                        // entire point.
                        .requestMatchers(HttpMethod.POST, "/api/requests").hasRole("REQUESTER")
                        .requestMatchers(HttpMethod.POST, "/api/requests/*/cancel", "/api/requests/*/fulfil")
                            .hasRole("REQUESTER")
                        // SPEC-007: searching for donors is a requester's job,
                        // and only against their own request. The role gate is
                        // here; the ownership check is in the service, because
                        // a URL pattern cannot express "yours".
                        .requestMatchers(HttpMethod.GET, "/api/requests/*/donors").hasRole("REQUESTER")
                        // SPEC-008. Offering blood is a donor's act and answering
                        // an offer is a requester's; withdrawing is the donor's
                        // own word about their own intention, so a requester is
                        // gated out of it here rather than in the service.
                        .requestMatchers(HttpMethod.POST, "/api/requests/*/pledges").hasRole("DONOR")
                        .requestMatchers(HttpMethod.GET, "/api/requests/*/pledges").hasRole("REQUESTER")
                        .requestMatchers(HttpMethod.POST, "/api/pledges/*/accept", "/api/pledges/*/decline")
                            .hasRole("REQUESTER")
                        .requestMatchers(HttpMethod.POST, "/api/pledges/*/withdraw").hasRole("DONOR")
                        // The reveal and the reveal log are open to both roles:
                        // a requester's number is revealed to a donor exactly as
                        // a donor's is to a requester, and both may audit it. Who
                        // is party to which pledge is a question only the service
                        // can answer.
                        .requestMatchers(HttpMethod.GET, "/api/pledges/*/contact").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/me/reveals").authenticated()
                        // Reference data: places, not people. Authenticated
                        // rather than public because nothing in this project is
                        // readable without a token, and a client only needs these
                        // lists once it has one.
                        .requestMatchers(HttpMethod.GET, "/api/thanas", "/api/hospitals").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/requests", "/api/requests/*").authenticated()
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
