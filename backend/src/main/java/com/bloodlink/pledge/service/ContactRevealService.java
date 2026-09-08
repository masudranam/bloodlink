package com.bloodlink.pledge.service;

import com.bloodlink.pledge.ContactResponse;
import com.bloodlink.pledge.ContactReveal;
import com.bloodlink.pledge.ContactRevealRepository;
import com.bloodlink.pledge.CounterpartySummary;
import com.bloodlink.pledge.Pledge;
import com.bloodlink.pledge.PledgeRepository;
import com.bloodlink.pledge.PledgeStatus;
import com.bloodlink.pledge.RevealResponse;
import com.bloodlink.pledge.ViewerSummary;
import com.bloodlink.request.PageResponse;
import com.bloodlink.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place in BloodLink that hands out a phone number.
 *
 * <p>It is its own service for that reason. The privacy rule is easy to state
 * and easy to erode, and the erosion always looks like a small convenience
 * somewhere else — a phone field added to a list DTO, a number included in an
 * accept response "to save a click". Keeping the reveal in one class means there
 * is one place to read to know when a number leaves the database, and one
 * transaction in which the reveal and its audit row either both happen or
 * neither does.
 *
 * <p>Two guards, in this order: are you a party to this pledge, and has it been
 * accepted. The party check comes first on purpose. A stranger gets the same
 * 403 whatever the pledge's status, so the error cannot tell them whether two
 * other people have agreed to meet.
 */
@Service
public class ContactRevealService {

    private static final Logger LOG = LoggerFactory.getLogger(ContactRevealService.class);

    private final PledgeRepository pledges;
    private final ContactRevealRepository reveals;

    public ContactRevealService(PledgeRepository pledges, ContactRevealRepository reveals) {
        this.pledges = pledges;
        this.reveals = reveals;
    }

    /**
     * Reveals the other party's phone number, and records that it happened.
     *
     * <p>The audit row is written inside the same transaction as the read, and
     * before the response is composed. An unaudited reveal is worse than a
     * failed one, so if the insert fails the reveal fails with it.
     *
     * <p>Called twice, it writes two rows. A reveal is an event rather than a
     * state: the second look is as real as the first, and a log that recorded
     * only the first would be answering a different question from the one its
     * reader is asking.
     *
     * @param viewerUserId the authenticated caller
     * @param pledgeId     the pledge whose counterparty they want
     * @return the counterparty, with their number
     * @throws PledgeNotFoundException     if no such pledge exists
     * @throws NotAPartyException          if the caller is neither side of it
     * @throws PledgeNotAcceptedException  if the pledge is not ACCEPTED
     */
    @Transactional
    public ContactResponse reveal(long viewerUserId, long pledgeId) {
        Pledge pledge = pledges.findById(pledgeId)
                .orElseThrow(() -> new PledgeNotFoundException(pledgeId));

        AppUser donorUser = pledge.getDonor().getUser();
        AppUser requesterUser = pledge.getRequest().getRequester();

        boolean callerIsDonor = donorUser.getId().equals(viewerUserId);
        boolean callerIsRequester = requesterUser.getId().equals(viewerUserId);
        if (!callerIsDonor && !callerIsRequester) {
            throw new NotAPartyException();
        }
        if (pledge.getStatus() != PledgeStatus.ACCEPTED) {
            throw new PledgeNotAcceptedException(pledge.getStatus());
        }

        AppUser viewer = callerIsDonor ? donorUser : requesterUser;
        AppUser counterparty = callerIsDonor ? requesterUser : donorUser;

        ContactReveal audit = reveals.save(new ContactReveal(
                pledge.getId(),
                pledge.getRequest().getId(),
                viewer.getId(),
                viewer.getFullName(),
                viewer.getRole(),
                counterparty.getId()));

        // Ids and roles, never the number. The log records that a reveal
        // happened; the database records what was revealed. Putting the digits
        // here would undo the whole design in one line, in a file nobody audits
        // as carefully as the API.
        LOG.info("event=contact_revealed pledgeId={} requestId={} viewerUserId={} viewerRole={} "
                        + "revealedUserId={} revealId={}",
                pledge.getId(), pledge.getRequest().getId(), viewer.getId(), viewer.getRole(),
                counterparty.getId(), audit.getId());

        return new ContactResponse(
                pledge.getId(),
                pledge.getRequest().getId(),
                new CounterpartySummary(
                        counterparty.getFullName(),
                        counterparty.getRole(),
                        counterparty.getPhone()),
                audit.getRevealedAt());
    }

    /**
     * The reveals of the caller's own number, newest first.
     *
     * <p>This is what makes the log worth keeping. "Who has seen my number, and
     * why" is the question the whole project exists to answer, and a log only
     * the operator can read is a reassurance rather than an answer.
     *
     * <p>It returns no phone number at all — not the viewer's, and not the
     * caller's own. Echoing back a number the caller already knows would put one
     * into a paged list response, which is the exact shape this project refuses.
     *
     * @param userId the authenticated caller
     * @param page   zero-based page number
     * @param size   page size
     * @return a page of reveals of their number
     */
    @Transactional(readOnly = true)
    public PageResponse<RevealResponse> myReveals(long userId, int page, int size) {
        Page<ContactReveal> found = reveals.findByRevealedUserIdOrderByRevealedAtDesc(
                userId, PageRequest.of(page, size));
        return PageResponse.of(found.map(ContactRevealService::toResponse));
    }

    private static RevealResponse toResponse(ContactReveal reveal) {
        return new RevealResponse(
                reveal.getId(),
                reveal.getRequestId(),
                reveal.getPledgeId(),
                new ViewerSummary(reveal.getViewerName(), reveal.getViewerRole()),
                reveal.getRevealedAt());
    }

    /**
     * How many times the caller's number has been revealed.
     *
     * @param userId the authenticated caller
     * @return the count, for a client that wants to show it without paging
     */
    @Transactional(readOnly = true)
    public long myRevealCount(long userId) {
        return reveals.countByRevealedUserId(userId);
    }
}
