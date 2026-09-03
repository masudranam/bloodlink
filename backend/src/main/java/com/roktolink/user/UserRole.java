package com.roktolink.user;

/**
 * The two actors in RoktoLink.
 *
 * <p>They are separate roles rather than a permission flag because they want
 * opposite things: a donor publishes availability and pledges, a requester
 * publishes need and accepts. Conflating them would let anyone manufacture a
 * request to harvest contact details, which is the failure mode this project
 * exists to fix.
 */
public enum UserRole {

    DONOR,
    REQUESTER
}
