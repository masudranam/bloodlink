package com.roktolink.auth;

import com.roktolink.user.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A registration.
 *
 * @param fullName display name, what a requester or donor is called
 * @param phone    a Bangladeshi mobile in any of the usual forms
 * @param password plaintext, hashed immediately and never stored
 * @param role     DONOR or REQUESTER
 */
public record RegisterRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 120, message = "must be at most 120 characters")
        String fullName,

        // The plus is written as a character class rather than escaped, so the
        // pattern reads the same in Java, in SQL and in the spec.
        @NotBlank(message = "must not be blank")
        @Pattern(regexp = "^(?:[+]?880)?0?1[3-9][0-9]{8}$",
                 message = "must be a Bangladeshi mobile number")
        String phone,

        // 72 is BCrypt's ceiling: it silently ignores anything past 72 bytes, so a
        // longer password would give a false sense of strength.
        @NotBlank(message = "must not be blank")
        @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        String password,

        @NotNull(message = "must be DONOR or REQUESTER")
        UserRole role) {
}
