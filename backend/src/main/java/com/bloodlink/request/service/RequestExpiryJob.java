package com.bloodlink.request.service;

import com.bloodlink.request.BloodRequest;
import com.bloodlink.request.BloodRequestRepository;
import com.bloodlink.request.BloodRequestStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retires requests whose date has passed.
 *
 * <p>A request nobody cancels is the buried-post problem in a new form: the
 * patient was treated, the requester moved on, and the entry sits in the feed
 * wasting the time of every donor who reads it. Expiring it cannot depend on
 * somebody remembering.
 *
 * <p><strong>This job changes nothing directly.</strong> It finds candidates and
 * calls {@link BloodRequestService#transition}, so
 * {@link BloodRequestStateMachine} decides every move. That matters more here
 * than anywhere else in the project: a background process has no user to answer
 * to, and the tempting version of this class is one
 * {@code update blood_request set status = 'EXPIRED'} that would happily expire
 * a request somebody had already fulfilled.
 *
 * <p>The {@link Clock} bean is zoned {@code Asia/Dhaka}, the same zone
 * eligibility is computed in. "Past its date" and "eligible today" are both
 * questions about the calendar day in Dhaka, and answering them in two
 * different zones would put the two features six hours apart.
 *
 * <p>The bean does not exist when {@code bloodlink.expiry.enabled} is false, so
 * switching it off schedules nothing rather than scheduling something that
 * returns immediately.
 */
@Component
@ConditionalOnProperty(name = "bloodlink.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class RequestExpiryJob {

    private static final Logger LOG = LoggerFactory.getLogger(RequestExpiryJob.class);

    /** The two statuses a request can expire out of. Both are legal moves. */
    private static final Set<BloodRequestStatus> EXPIRABLE =
            EnumSet.of(BloodRequestStatus.OPEN, BloodRequestStatus.PLEDGED);

    private final BloodRequestRepository requests;
    private final BloodRequestService requestService;
    private final Clock clock;

    public RequestExpiryJob(BloodRequestRepository requests,
                            BloodRequestService requestService,
                            Clock clock) {
        this.requests = requests;
        this.requestService = requestService;
        this.clock = clock;
    }

    /**
     * Expires every live request whose date has gone.
     *
     * <p>Idempotent: a second run finds nothing, because the first one moved
     * every candidate out of {@link #EXPIRABLE}.
     *
     * @return how many were expired
     */
    @Scheduled(cron = "${bloodlink.expiry.cron:0 5 * * * *}")
    @Transactional
    public int expireStaleRequests() {
        LocalDate today = LocalDate.now(clock);
        List<BloodRequest> stale =
                requests.findByStatusInAndNeededByBeforeOrderByNeededByAsc(EXPIRABLE, today);

        int expired = 0;
        for (BloodRequest request : stale) {
            BloodRequestStatus previous = request.getStatus();
            requestService.transition(request.getId(), BloodRequestStatus.EXPIRED);
            expired += 1;
            LOG.info("event=request_expired requestId={} previousStatus={} neededBy={} today={}",
                    request.getId(), previous, request.getNeededBy(), today);
        }

        if (expired > 0) {
            LOG.info("event=expiry_run expired={} today={}", expired, today);
        } else {
            // A job that says "expired 0 requests" every hour trains everybody to
            // stop reading its output, which is the opposite of the point.
            LOG.debug("event=expiry_run expired=0 today={}", today);
        }

        return expired;
    }
}
