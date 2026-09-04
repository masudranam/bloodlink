package com.bloodlink.auth;

import com.bloodlink.user.UserRole;

/**
 * The result of registering.
 *
 * <p>No phone number: the caller supplied their own, and the server does not
 * echo phone numbers anywhere (SPEC-003 AC-9).
 *
 * @param id       the new user's id
 * @param fullName display name as stored
 * @param role     the role registered
 */
public record RegisterResponse(Long id, String fullName, UserRole role) {
}
