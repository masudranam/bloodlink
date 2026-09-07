package com.bloodlink.request;

/**
 * Who is asking.
 *
 * A name and an id, and deliberately nothing else. A donor deciding whether to
 * cross Dhaka deserves to know who is asking; their phone number arrives only
 * after a pledge is accepted, through SPEC-008, and is written to the audit log
 * when it does.
 *
 * @param id       the requester's user id
 * @param fullName their display name
 */
public record RequesterSummary(Long id, String fullName) {
}
