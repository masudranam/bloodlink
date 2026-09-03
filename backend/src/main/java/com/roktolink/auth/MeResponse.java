package com.roktolink.auth;

import com.roktolink.user.UserRole;

/**
 * Who the bearer of this token is. No phone number, by SPEC-003 AC-9.
 *
 * @param id       the user's id
 * @param fullName display name
 * @param role     DONOR or REQUESTER
 */
public record MeResponse(Long id, String fullName, UserRole role) {
}
