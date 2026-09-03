package com.roktolink.donor;

/**
 * A thana as shown to a client: a name, not coordinates.
 *
 * Deliberately coarse. The latitude and longitude behind it are used for
 * distance ranking in SPEC-007 and are never sent to a client.
 *
 * @param id       the thana id
 * @param name     for example Dhanmondi
 * @param district for example Dhaka
 */
public record ThanaSummary(Long id, String name, String district) {
}
