package com.roktolink.donor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.roktolink.donor.BloodGroup;
import com.roktolink.donor.DonorProfile;
import com.roktolink.donor.DonorProfileRepository;
import com.roktolink.donor.DonorProfileRequest;
import com.roktolink.donor.DonorProfileResponse;
import com.roktolink.reference.Thana;
import com.roktolink.reference.ThanaRepository;
import com.roktolink.user.AppUser;
import com.roktolink.user.AppUserRepository;
import com.roktolink.user.UserRole;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The branches of the donor profile service that decide a status code, plus the
 * one property that matters most: a response carries computed eligibility and no
 * phone number.
 */
@ExtendWith(MockitoExtension.class)
class DonorProfileServiceTest {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final Instant NOON_IN_DHAKA = Instant.parse("2026-09-03T06:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);
    private static final long USER_ID = 7L;
    private static final long THANA_ID = 12L;

    @Mock
    private DonorProfileRepository profiles;

    @Mock
    private AppUserRepository users;

    @Mock
    private ThanaRepository thanas;

    private DonorProfileService service;

    @BeforeEach
    void setUp() {
        EligibilityCalculator eligibility = new EligibilityCalculator(
                Clock.fixed(NOON_IN_DHAKA, DHAKA),
                new EligibilityProperties(90, DHAKA));
        service = new DonorProfileService(profiles, users, thanas, eligibility);
    }

    // ---------- AC-1: create ----------

    @Test
    void ac1_create_savesTheProfileAndReturnsComputedEligibility() {
        when(profiles.existsByUserId(USER_ID)).thenReturn(false);
        when(users.findById(USER_ID)).thenReturn(Optional.of(donorUser()));
        when(thanas.findById(THANA_ID)).thenReturn(Optional.of(dhanmondi()));
        when(profiles.save(any(DonorProfile.class))).thenAnswer(call -> call.getArgument(0));

        DonorProfileResponse response = service.create(USER_ID, new DonorProfileRequest(
                BloodGroup.B_POSITIVE, THANA_ID, TODAY.minusDays(30), true));

        assertThat(response.bloodGroup()).isEqualTo(BloodGroup.B_POSITIVE);
        assertThat(response.thana().name()).isEqualTo("Dhanmondi");
        assertThat(response.available()).isTrue();
        assertThat(response.isEligible()).isFalse();
        assertThat(response.nextEligibleDate()).isEqualTo(TODAY.minusDays(30).plusDays(91));
        assertThat(response.donationIntervalDays()).isEqualTo(90);
        verify(profiles).save(any(DonorProfile.class));
    }

    @Test
    void ac1_create_defaultsAvailabilityToTrueWhenOmitted() {
        when(profiles.existsByUserId(USER_ID)).thenReturn(false);
        when(users.findById(USER_ID)).thenReturn(Optional.of(donorUser()));
        when(thanas.findById(THANA_ID)).thenReturn(Optional.of(dhanmondi()));
        when(profiles.save(any(DonorProfile.class))).thenAnswer(call -> call.getArgument(0));

        DonorProfileResponse response = service.create(USER_ID, new DonorProfileRequest(
                BloodGroup.O_NEGATIVE, THANA_ID, null, null));

        assertThat(response.available()).isTrue();
        assertThat(response.isEligible()).isTrue();
        assertThat(response.nextEligibleDate()).isNull();
    }

    // ---------- AC-2: only one profile per donor ----------

    @Test
    void ac2_create_rejectsASecondProfileForTheSameDonor() {
        when(profiles.existsByUserId(USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.create(USER_ID, new DonorProfileRequest(
                BloodGroup.A_POSITIVE, THANA_ID, null, true)))
                .isInstanceOf(ProfileAlreadyExistsException.class);

        verify(profiles, never()).save(any());
    }

    // ---------- AC-4 and AC-6: read, and the absence of a profile ----------

    @Test
    void ac4_get_returnsTheProfileWithEligibilityComputedNow() {
        when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(profileLastDonating(TODAY.minusDays(200))));

        DonorProfileResponse response = service.get(USER_ID);

        assertThat(response.isEligible()).isTrue();
        assertThat(response.lastDonationDate()).isEqualTo(TODAY.minusDays(200));
    }

    @Test
    void ac4_get_failsWhenTheDonorHasNoProfile() {
        when(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER_ID)).isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void ac6_delete_failsWhenThereIsNothingToDelete() {
        when(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID)).isInstanceOf(ProfileNotFoundException.class);
        verify(profiles, never()).delete(any());
    }

    @Test
    void ac6_delete_removesTheProfile() {
        DonorProfile existing = profileLastDonating(null);
        when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        service.delete(USER_ID);

        verify(profiles).delete(existing);
    }

    // ---------- AC-5: replace is a replacement, not a merge ----------

    @Test
    void ac5_replace_clearsTheDonationDateWhenItIsOmitted() {
        DonorProfile existing = profileLastDonating(TODAY.minusDays(10));
        when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
        when(thanas.findById(THANA_ID)).thenReturn(Optional.of(dhanmondi()));
        when(profiles.save(any(DonorProfile.class))).thenAnswer(call -> call.getArgument(0));

        DonorProfileResponse response = service.replace(USER_ID, new DonorProfileRequest(
                BloodGroup.AB_NEGATIVE, THANA_ID, null, false));

        ArgumentCaptor<DonorProfile> saved = ArgumentCaptor.forClass(DonorProfile.class);
        verify(profiles).save(saved.capture());

        assertThat(saved.getValue().getLastDonationDate()).isNull();
        assertThat(saved.getValue().getBloodGroup()).isEqualTo(BloodGroup.AB_NEGATIVE);
        assertThat(response.available()).isFalse();
        assertThat(response.isEligible()).isTrue();
        assertThat(response.nextEligibleDate()).isNull();
    }

    @Test
    void ac5_replace_failsWhenThereIsNoProfile() {
        when(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replace(USER_ID, new DonorProfileRequest(
                BloodGroup.A_NEGATIVE, THANA_ID, null, true)))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    // ---------- AC-11: well-formed but wrong ----------

    @Test
    void ac11_create_rejectsADonationDateInTheFuture() {
        when(profiles.existsByUserId(USER_ID)).thenReturn(false);
        when(users.findById(USER_ID)).thenReturn(Optional.of(donorUser()));
        when(thanas.findById(THANA_ID)).thenReturn(Optional.of(dhanmondi()));

        assertThatThrownBy(() -> service.create(USER_ID, new DonorProfileRequest(
                BloodGroup.B_NEGATIVE, THANA_ID, TODAY.plusDays(1), true)))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessage("must not be in the future");

        verify(profiles, never()).save(any());
    }

    @Test
    void ac11_create_acceptsADonationDateOfToday() {
        when(profiles.existsByUserId(USER_ID)).thenReturn(false);
        when(users.findById(USER_ID)).thenReturn(Optional.of(donorUser()));
        when(thanas.findById(THANA_ID)).thenReturn(Optional.of(dhanmondi()));
        when(profiles.save(any(DonorProfile.class))).thenAnswer(call -> call.getArgument(0));

        DonorProfileResponse response = service.create(USER_ID, new DonorProfileRequest(
                BloodGroup.B_NEGATIVE, THANA_ID, TODAY, true));

        assertThat(response.lastDonationDate()).isEqualTo(TODAY);
        assertThat(response.isEligible()).isFalse();
    }

    @Test
    void ac11_create_rejectsAnUnknownThana() {
        when(profiles.existsByUserId(USER_ID)).thenReturn(false);
        when(users.findById(USER_ID)).thenReturn(Optional.of(donorUser()));
        when(thanas.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(USER_ID, new DonorProfileRequest(
                BloodGroup.O_POSITIVE, 999L, null, true)))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining("no such thana");
    }

    // ---------- AC-12: the response has no phone field at all ----------

    @Test
    void ac12_theResponseTypeHasNoPhoneComponent() {
        assertThat(DonorProfileResponse.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("phone"));
        assertThat(com.roktolink.donor.ThanaSummary.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("phone"));
    }

    private AppUser donorUser() {
        return new AppUser("Masud Rana", "+8801712345678", "$2a$10$notarealhash", UserRole.DONOR);
    }

    private Thana dhanmondi() {
        Thana thana = newThana();
        thana.setName("Dhanmondi");
        thana.setDistrict("Dhaka");
        thana.setLatitude(new BigDecimal("23.746100"));
        thana.setLongitude(new BigDecimal("90.374200"));
        setId(thana, THANA_ID);
        return thana;
    }

    private DonorProfile profileLastDonating(LocalDate lastDonationDate) {
        return new DonorProfile(donorUser(), BloodGroup.B_POSITIVE, dhanmondi(), lastDonationDate, true);
    }

    /**
     * Thana's constructor is protected for JPA and its id is database-generated,
     * so a test double needs reflection rather than a setter that production code
     * has no reason to expose.
     */
    private Thana newThana() {
        try {
            var constructor = Thana.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException cannotHappen) {
            throw new IllegalStateException(cannotHappen);
        }
    }

    private void setId(Object entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException cannotHappen) {
            throw new IllegalStateException(cannotHappen);
        }
    }
}
