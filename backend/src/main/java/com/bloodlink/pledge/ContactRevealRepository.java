package com.bloodlink.pledge;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * The audit log, and the two things anybody may do with it: append, and read
 * back the reveals of your own number.
 *
 * Deliberately not a JpaRepository. Extending it would publish deleteById,
 * deleteAll and saveAll over an append-only table, and the delete methods have no
 * business existing at all. This exposes exactly three operations.
 */
public interface ContactRevealRepository extends Repository<ContactReveal, Long> {

    ContactReveal save(ContactReveal reveal);

    Page<ContactReveal> findByRevealedUserIdOrderByRevealedAtDesc(Long revealedUserId, Pageable pageable);

    long countByRevealedUserId(Long revealedUserId);
}
