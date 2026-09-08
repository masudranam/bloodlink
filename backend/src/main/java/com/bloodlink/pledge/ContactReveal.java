package com.bloodlink.pledge;

import com.bloodlink.user.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A record that somebody saw somebody else's phone number.
 *
 * <p>Append-only. There is no setter, no update hook and no delete path: the row
 * states that an event happened, and events do not change their minds.
 *
 * <p>It holds plain ids rather than associations, matching the migration's
 * deliberate absence of foreign keys. A cascade could delete the record of a
 * reveal, and a restrict could stop a donor deleting their own profile; neither
 * is acceptable, so the log outlives its subjects in both directions.
 *
 * <p>{@code viewerName} is stored rather than resolved on read. It records who
 * this person was at the moment they looked, which is a historical fact — unlike
 * eligibility, which is a question that keeps changing and must never be stored.
 */
@Entity
@Table(name = "contact_reveal")
public class ContactReveal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pledge_id", nullable = false)
    private Long pledgeId;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "viewer_user_id", nullable = false)
    private Long viewerUserId;

    @Column(name = "viewer_name", nullable = false, length = 120)
    private String viewerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "viewer_role", nullable = false, length = 16)
    private UserRole viewerRole;

    @Column(name = "revealed_user_id", nullable = false)
    private Long revealedUserId;

    @Column(name = "revealed_at", nullable = false)
    private Instant revealedAt;

    protected ContactReveal() {
        // for JPA
    }

    /**
     * Records one reveal.
     *
     * @param pledgeId       the pledge that entitled the viewer to look
     * @param requestId      the request behind it, stored so the log needs no
     *                       join to explain itself
     * @param viewerUserId   who looked
     * @param viewerName     their name at the time of looking
     * @param viewerRole     the role they looked in
     * @param revealedUserId whose number they saw
     */
    public ContactReveal(Long pledgeId, Long requestId, Long viewerUserId,
                         String viewerName, UserRole viewerRole, Long revealedUserId) {
        this.pledgeId = pledgeId;
        this.requestId = requestId;
        this.viewerUserId = viewerUserId;
        this.viewerName = viewerName;
        this.viewerRole = viewerRole;
        this.revealedUserId = revealedUserId;
    }

    @PrePersist
    void onCreate() {
        revealedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPledgeId() {
        return pledgeId;
    }

    public Long getRequestId() {
        return requestId;
    }

    public Long getViewerUserId() {
        return viewerUserId;
    }

    public String getViewerName() {
        return viewerName;
    }

    public UserRole getViewerRole() {
        return viewerRole;
    }

    public Long getRevealedUserId() {
        return revealedUserId;
    }

    public Instant getRevealedAt() {
        return revealedAt;
    }
}
