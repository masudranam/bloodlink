package com.roktolink.donor;

import com.roktolink.reference.Thana;
import com.roktolink.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * What a donor offers: a blood group, a thana, and when they last gave blood.
 *
 * <p>There is no eligibility field here, and there must never be one. Eligibility
 * is {@code lastDonationDate + interval < today}, derived on read every time
 * (SPEC-004). A stored flag is correct on the day it is written and wrong every
 * day afterwards.
 *
 * <p>Location is the thana, not the donor's own coordinates. Holding a home
 * address accurate to a few metres would sit badly in a project whose point is
 * that contact details stay private until both sides agree.
 */
@Entity
@Table(name = "donor_profile")
public class DonorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "blood_group", nullable = false, length = 3)
    private BloodGroup bloodGroup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thana_id", nullable = false)
    private Thana thana;

    /** Null means this donor has never recorded a donation. */
    @Column(name = "last_donation_date")
    private LocalDate lastDonationDate;

    @Column(name = "available", nullable = false)
    private boolean available = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DonorProfile() {
        // for JPA
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

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public BloodGroup getBloodGroup() {
        return bloodGroup;
    }

    public void setBloodGroup(BloodGroup bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public Thana getThana() {
        return thana;
    }

    public void setThana(Thana thana) {
        this.thana = thana;
    }

    public LocalDate getLastDonationDate() {
        return lastDonationDate;
    }

    public void setLastDonationDate(LocalDate lastDonationDate) {
        this.lastDonationDate = lastDonationDate;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
