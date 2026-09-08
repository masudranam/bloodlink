package com.bloodlink.request;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Lookups over blood requests. */
public interface BloodRequestRepository extends JpaRepository<BloodRequest, Long> {

    Page<BloodRequest> findByStatus(BloodRequestStatus status, Pageable pageable);

    /**
     * Requests that are still live but whose date has gone.
     *
     * Served by ix_blood_request_status_needed_by from V4, which was created for
     * this reader and says so in its comment.
     *
     * The comparison is strict: a request needed TODAY is still live, and
     * expiring it would remove it from the feed on the worst possible morning.
     *
     * @param statuses the statuses that can still expire
     * @param today    the current date in the configured zone
     * @return the requests to expire, oldest first
     */
    List<BloodRequest> findByStatusInAndNeededByBeforeOrderByNeededByAsc(
            Collection<BloodRequestStatus> statuses, LocalDate today);
}
