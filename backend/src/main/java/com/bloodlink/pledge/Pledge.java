package com.bloodlink.pledge;

import com.bloodlink.donor.DonorProfile;
import com.bloodlink.request.BloodRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A donor offering blood for one request.
 *
 * <p>The pledge is what makes the privacy rule possible: it is a relationship
 * between two people that either of them can point at. Before it exists there is
 * no reason for them to have each other's numbers, and after the requester
 * accepts it there is.
 *
 * <p>It holds no phone number and no copy of one. Both parties' numbers stay on
 * {@code app_user}, reachable through the request's requester and the donor's
 * user, and they leave only through the one endpoint that audits doing so.
 */
@Entity
@Table(name = "pledge")
public class Pledge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private BloodRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donor_id", nullable = false)
    private DonorProfile donor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PledgeStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Pledge() {
        // for JPA
    }

    /**
     * Makes a pledge, always PENDING.
     *
     * <p>There is no constructor taking a status. A pledge cannot begin accepted,
     * and every later change goes through {@code PledgeStateMachine}.
     *
     * @param request the request being pledged against
     * @param donor   the donor offering
     */
    public Pledge(BloodRequest request, DonorProfile donor) {
        this.request = request;
        this.donor = donor;
        this.status = PledgeStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Applies a status the state machine has already allowed.
     *
     * <p>Deliberately not a setter: the name says that the decision was made
     * elsewhere, and nothing in this project sets a status without asking first.
     *
     * @param next the status to move to
     */
    public void applyStatus(PledgeStatus next) {
        this.status = next;
    }

    public Long getId() {
        return id;
    }

    public BloodRequest getRequest() {
        return request;
    }

    public DonorProfile getDonor() {
        return donor;
    }

    public PledgeStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * When the pledge last changed status, or null while it is still PENDING.
     *
     * <p>Derived from {@code updatedAt} rather than stored separately: a pledge
     * has exactly one status change worth dating, and a PENDING pledge has had
     * none.
     *
     * @return the decision timestamp, or null
     */
    public Instant getDecidedAt() {
        return status == PledgeStatus.PENDING ? null : updatedAt;
    }
}
