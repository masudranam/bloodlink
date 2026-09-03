package com.roktolink.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * A login attempt.
 *
 * @param phone    the registered phone, in any of the accepted forms
 * @param password plaintext, compared against the stored BCrypt hash
 */
public record LoginRequest(

        @NotBlank(message = "must not be blank")
        String phone,

        @NotBlank(message = "must not be blank")
        String password) {
}
