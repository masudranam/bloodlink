package com.bloodlink.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Lookups over {@link AppUser}.
 *
 * <p>Phone is the login identifier, and is always stored canonicalised, so a
 * lookup is an exact match rather than anything fuzzy.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByPhone(String phone);

    boolean existsByPhone(String phone);
}
