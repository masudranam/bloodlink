package com.roktolink.auth.jwt;

import com.roktolink.user.AppUser;
import java.time.Instant;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Mints access tokens.
 *
 * <p>The subject is the {@code app_user} id and there is a single {@code role}
 * claim, which Spring Security turns into {@code ROLE_DONOR} or
 * {@code ROLE_REQUESTER}. Nothing else goes in: a token is read by anyone who
 * holds it, so it carries no name and no phone number.
 */
@Component
public class JwtIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public JwtIssuer(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    /**
     * Issues a signed token for a user.
     *
     * @param user the authenticated user
     * @return the compact serialised JWT
     */
    public String issue(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("roktolink")
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .claim("role", user.getRole().name())
                .build();

        JwsHeader header = JwsHeader.with(SecurityConfig.JWS_ALGORITHM).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * The lifetime of an issued token, for the {@code expiresIn} field of a login
     * response.
     *
     * @return seconds until a freshly issued token expires
     */
    public long ttlSeconds() {
        return properties.ttl().toSeconds();
    }
}
