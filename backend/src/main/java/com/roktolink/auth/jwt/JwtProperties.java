package com.roktolink.auth.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT signing configuration.
 *
 * @param secret HMAC signing secret, at least 32 bytes. Never the shipped default
 *               outside development — set {@code ROKTOLINK_JWT_SECRET}.
 * @param ttl    how long an issued token stays valid. There is no refresh token,
 *               so this is also how long a session lasts.
 */
@ConfigurationProperties(prefix = "roktolink.security.jwt")
public record JwtProperties(String secret, Duration ttl) {
}
