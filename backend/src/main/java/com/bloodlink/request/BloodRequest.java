package com.bloodlink.request;

import com.bloodlink.donor.BloodGroup;
import com.bloodlink.reference.Hospital;
import com.bloodlink.user.AppUser;
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
import java.time.LocalDate;

/**
 * Somebody needs blood.
 *
 * <p>The status is the one piece of state in this project that is genuinely
 * stored rather than derived. It records what people did — cancelled, confirmed —
 * and cannot be recomputed from any other column.
 *
 * <p>The requester's phone lives on {@link AppUser} and never leaves it through
 * anything in SPEC-006. A donor learns who is asking, not how to call them.
 */
@Entity
@Table(name = "blood_request")
public class BloodRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private AppUser requester;

    @Column(name = "patient_blood_group", nullable = false, length = 3)
    private BloodGroup patientBloodGroup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hospital_id", nullable = false)
    private Hospital hospital;

    @Column(name = "units_needed", nullable = false)
    private short unitsNeeded;

    @Column(name = "needed_by", nullable = false)
    private LocalDate neededBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BloodRequestStatus status;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BloodRequest() {
        // for JPA
    }

    /**
     * Raises a new request, always OPEN.
     *
     * <p>There is no constructor that takes a status: a request cannot be created
     * in any other state, and every later change goes through the state machine.
     *
     * @param requester         who is asking
     * @param patientBloodGroup what the patient needs
     * @param hospital          where to come
     * @param unitsNeeded       how many units, 1 to 10
     * @param neededBy          the date after which this stops being useful
     * @param note              free text for the donor, or null
     */
    public BloodRequest(AppUser requester, BloodGroup patientBloodGroup, Hospital hospital,
                        short unitsNeeded, LocalDate neededBy, String note) {
        this.requester = requester;
        this.patientBloodGroup = patientBloodGroup;
        this.hospital = hospital;
        this.unitsNeeded = unitsNeeded;
        this.neededBy = neededBy;
        this.note = note;
        this.status = BloodRequestStatus.OPEN;
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
     * Moves this request to a new status.
     *
     * <p>The only legitimate caller is {@code BloodRequestService}, which asks
     * the state machine first. It cannot be package-private because the service
     * lives in a subpackage, so the guarantee is the constructor's instead: a
     * request can only be built as OPEN, and this is the one way it ever changes.
     *
     * @param next the new status, already checked
     */
    public void applyStatus(BloodRequestStatus next) {
        this.status = next;
    }

    public Long getId() {
        return id;
    }

    public AppUser getRequester() {
        return requester;
    }

    public BloodGroup getPatientBloodGroup() {
        return patientBloodGroup;
    }

    public Hospital getHospital() {
        return hospital;
    }

    public short getUnitsNeeded() {
        return unitsNeeded;
    }

    public LocalDate getNeededBy() {
        return neededBy;
    }

    public BloodRequestStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
