package com.bloodlink.pledge;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Lookups over pledges. */
public interface PledgeRepository extends JpaRepository<Pledge, Long> {

    Page<Pledge> findByRequestIdOrderByCreatedAtDesc(Long requestId, Pageable pageable);

    Page<Pledge> findByDonorIdOrderByCreatedAtDesc(Long donorId, Pageable pageable);

    boolean existsByRequestIdAndDonorId(Long requestId, Long donorId);

    /**
     * How many pledges on this request still count.
     *
     * Both routes back to OPEN depend on this: when a decline or a withdrawal
     * leaves no active pledge, the request returns to the feed rather than
     * sitting in PLEDGED with nobody coming.
     *
     * @param requestId the request
     * @param statuses  the statuses that count as active
     * @return how many pledges match
     */
    long countByRequestIdAndStatusIn(Long requestId, Collection<PledgeStatus> statuses);

    Optional<Pledge> findByRequestIdAndDonorId(Long requestId, Long donorId);
}
