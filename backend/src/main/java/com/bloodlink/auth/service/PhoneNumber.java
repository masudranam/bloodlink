package com.bloodlink.auth.service;

/**
 * Canonicalises Bangladeshi mobile numbers.
 *
 * <p>People type their own number several ways — {@code 01712345678},
 * {@code 8801712345678}, {@code +880 1712-345678}. All of them are the same
 * person, so all of them normalise to {@code +8801712345678} before anything
 * reaches the database. Without this, one person could hold two accounts and the
 * duplicate check in {@link AuthService} would be decorative.
 */
public final class PhoneNumber {

    private static final String CANONICAL_PREFIX = "+880";
    private static final String NATIONAL_PATTERN = "1[3-9][0-9]{8}";

    private PhoneNumber() {
        // utility
    }

    /**
     * Normalises a phone number as typed into canonical E.164 form.
     *
     * @param raw the number as submitted, possibly with spaces or dashes
     * @return the number as {@code +8801XXXXXXXXX}
     * @throws IllegalArgumentException if it is not a Bangladeshi mobile number
     */
    public static String normalise(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Phone number is required");
        }

        String digits = raw.replaceAll("[\\s\\-()]", "");
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }
        if (digits.startsWith("880")) {
            digits = digits.substring(3);
        }
        if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }

        if (!digits.matches(NATIONAL_PATTERN)) {
            throw new IllegalArgumentException("Not a Bangladeshi mobile number: " + raw);
        }
        return CANONICAL_PREFIX + digits;
    }
}
