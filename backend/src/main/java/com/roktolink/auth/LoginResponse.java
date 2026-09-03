package com.roktolink.auth;

import com.roktolink.user.UserRole;

/**
 * A freshly issued access token.
 *
 * @param accessToken the signed JWT
 * @param tokenType   always {@code Bearer}
 * @param expiresIn   seconds until the token expires; there is no refresh token
 * @param role        the role the token carries, so a client can route on it
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserRole role) {
}
