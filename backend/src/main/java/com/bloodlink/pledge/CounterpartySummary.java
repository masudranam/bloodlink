package com.bloodlink.pledge;

import com.bloodlink.user.UserRole;

/**
 * The other party to an accepted pledge, with their phone number.
 *
 * This is the only type in BloodLink that carries a phone number outwards, and
 * it is reachable only from the one endpoint that writes an audit row for doing
 * so. Nothing else may embed it.
 *
 * @param fullName who they are
 * @param role     which side of the pledge they are
 * @param phone    the number, in canonical +8801XXXXXXXXX form
 */
public record CounterpartySummary(String fullName, UserRole role, String phone) {
}
