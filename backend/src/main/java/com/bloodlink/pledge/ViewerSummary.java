package com.bloodlink.pledge;

import com.bloodlink.user.UserRole;

/**
 * Somebody who looked at your number.
 *
 * No phone number of their own: knowing who looked at yours does not entitle you
 * to theirs.
 *
 * @param fullName their name at the time they looked
 * @param role     the role they looked in
 */
public record ViewerSummary(String fullName, UserRole role) {
}
