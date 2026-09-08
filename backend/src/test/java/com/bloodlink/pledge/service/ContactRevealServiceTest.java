package com.bloodlink.pledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bloodlink.donor.BloodGroup;
import com.bloodlink.donor.DonorProfile;
import com.bloodlink.pledge.ContactResponse;
import com.bloodlink.pledge.ContactReveal;
import com.bloodlink.pledge.ContactRevealRepository;
import com.bloodlink.pledge.Pledge;
import com.bloodlink.pledge.PledgeRepository;
import com.bloodlink.pledge.PledgeStatus;
import com.bloodlink.pledge.RevealResponse;
import com.bloodlink.reference.Hospital;
import com.bloodlink.reference.Thana;
import com.bloodlink.request.BloodRequest;
import com.bloodlink.request.BloodRequestStatus;
import com.bloodlink.request.PageResponse;
import com.bloodlink.user.AppUser;
import com.bloodlink.user.UserRole;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

/**
 * The one endpoint in BloodLink that hands out a phone number.
 *
 * <p>Everything here is either a guard on that or a check that the reveal was
 * written down. The two are inseparable by design: an unaudited reveal is worse
 * than a failed one, so the audit row is asserted on the success path and its
 * absence is asserted on every refusal.
 */
@ExtendWith(MockitoExtension.class)
class ContactRevealServiceTest {

    private static final long REQUESTER_USER_ID = 5L;
    private static final long DONOR_USER_ID = 7L;
    private static final long STRANGER_ID = 9L;
    private static final long REQUEST_ID = 3L;
    private static final long PLEDGE_ID = 4L;

    private static final String DONOR_PHONE = "+8801712000007";
    private static final String REQUESTER_PHONE = "+8801712000005";

    @Mock
    private PledgeRepository pledges;

    @Mock
    private ContactRevealRepository reveals;

    private ContactRevealService service;

    @BeforeEach
    void setUp() {
        service = new ContactRevealService(pledges, reveals);
    }

    // ---------- AC-15: the reveal, and what it takes to earn it ----------

    @Test
    void ac15_theRequesterSeesTheDonorsNumberOnceAccepted() {
        givenAnAcceptedPledge();

        ContactResponse response = service.reveal(REQUESTER_USER_ID, PLEDGE_ID);

        assertThat(response.counterparty().fullName()).isEqualTo("Rahim Uddin");
        assertThat(response.counterparty().role()).isEqualTo(UserRole.DONOR);
        assertThat(response.counterparty().phone()).isEqualTo(DONOR_PHONE);
        assertThat(response.pledgeId()).isEqualTo(PLEDGE_ID);
        assertThat(response.requestId()).isEqualTo(REQUEST_ID);
    }

    @Test
    void ac15_theDonorSeesTheRequestersNumber() {
        givenAnAcceptedPledge();

        ContactResponse response = service.reveal(DONOR_USER_ID, PLEDGE_ID);

        assertThat(response.counterparty().fullName()).isEqualTo("Karim Ahmed");
        assertThat(response.counterparty().role()).isEqualTo(UserRole.REQUESTER);
        assertThat(response.counterparty().phone())
                .as("symmetrical: the donor is not a supplicant, they get a number too")
                .isEqualTo(REQUESTER_PHONE);
    }

    @ParameterizedTest
    @EnumSource(value = PledgeStatus.class, names = {"PENDING", "DECLINED", "WITHDRAWN"})
    void ac15_nothingIsRevealedUntilThePledgeIsAccepted(PledgeStatus notAccepted) {
        Pledge pledge = pledgeInStatus(notAccepted);
        when(pledges.findById(PLEDGE_ID)).thenReturn(Optional.of(pledge));

        assertThatThrownBy(() -> service.reveal(REQUESTER_USER_ID, PLEDGE_ID))
                .isInstanceOf(PledgeNotAcceptedException.class)
                .hasMessage("Contact details are shared only for an accepted pledge; this one is "
                        + notAccepted);

        verifyNoInteractions(reveals);
    }

    @Test
    void ac15_thePendingCaseIsRefusedToTheDonorAsWell() {
        Pledge pledge = pledgeInStatus(PledgeStatus.PENDING);
        when(pledges.findById(PLEDGE_ID)).thenReturn(Optional.of(pledge));

        assertThatThrownBy(() -> service.reveal(DONOR_USER_ID, PLEDGE_ID))
                .as("an offer is not an introduction, in either direction")
                .isInstanceOf(PledgeNotAcceptedException.class);

        verify(reveals, never()).save(any());
    }

    // ---------- AC-16: a stranger, and the ordering of the two guards ----------

    @Test
    void ac16_aStrangerIsRefusedAndNothingIsWrittenDown() {
        givenAnAcceptedPledgeWithoutStubbingTheSave();

        assertThatThrownBy(() -> service.reveal(STRANGER_ID, PLEDGE_ID))
                .isInstanceOf(NotAPartyException.class);

        verifyNoInteractions(reveals);
    }

    @ParameterizedTest
    @EnumSource(PledgeStatus.class)
    void ac16_aStrangerGetsTheSameAnswerWhateverTheStatus(PledgeStatus status) {
        Pledge pledge = pledgeInStatus(status);
        when(pledges.findById(PLEDGE_ID)).thenReturn(Optional.of(pledge));

        assertThatThrownBy(() -> service.reveal(STRANGER_ID, PLEDGE_ID))
                .as("otherwise the error tells a stranger whether two other people have agreed to meet")
                .isInstanceOf(NotAPartyException.class);
    }

    @Test
    void ac16_aPledgeThatDoesNotExistIsNotFound() {
        when(pledges.findById(PLEDGE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reveal(REQUESTER_USER_ID, PLEDGE_ID))
                .isInstanceOf(PledgeNotFoundException.class)
                .hasMessage("No pledge with id 4");

        verifyNoInteractions(reveals);
    }

    // ---------- AC-17: every reveal is written down ----------

    @Test
    void ac17_aRevealAppendsOneRowNamingWhoLookedAndWhose() {
        givenAnAcceptedPledge();

        service.reveal(REQUESTER_USER_ID, PLEDGE_ID);

        ArgumentCaptor<ContactReveal> saved = ArgumentCaptor.forClass(ContactReveal.class);
        verify(reveals).save(saved.capture());

        ContactReveal row = saved.getValue();
        assertThat(row.getViewerUserId()).isEqualTo(REQUESTER_USER_ID);
        assertThat(row.getViewerName()).isEqualTo("Karim Ahmed");
        assertThat(row.getViewerRole()).isEqualTo(UserRole.REQUESTER);
        assertThat(row.getRevealedUserId()).isEqualTo(DONOR_USER_ID);
        assertThat(row.getPledgeId()).isEqualTo(PLEDGE_ID);
        assertThat(row.getRequestId()).isEqualTo(REQUEST_ID);
    }

    @Test
    void ac17_theRowRecordsTheDonorAsViewerWhenTheDonorLooks() {
        givenAnAcceptedPledge();

        service.reveal(DONOR_USER_ID, PLEDGE_ID);

        ArgumentCaptor<ContactReveal> saved = ArgumentCaptor.forClass(ContactReveal.class);
        verify(reveals).save(saved.capture());

        assertThat(saved.getValue().getViewerUserId()).isEqualTo(DONOR_USER_ID);
        assertThat(saved.getValue().getViewerRole()).isEqualTo(UserRole.DONOR);
        assertThat(saved.getValue().getRevealedUserId()).isEqualTo(REQUESTER_USER_ID);
    }

    @Test
    void ac17_lookingTwiceIsRecordedTwice() {
        givenAnAcceptedPledge();

        service.reveal(REQUESTER_USER_ID, PLEDGE_ID);
        service.reveal(REQUESTER_USER_ID, PLEDGE_ID);

        verify(reveals, times(2)).save(any(ContactReveal.class));
    }

    @Test
    void ac17_theResponseCarriesTheAuditRowsOwnTimestamp() {
        givenAnAcceptedPledge();

        ContactResponse response = service.reveal(REQUESTER_USER_ID, PLEDGE_ID);

        assertThat(response.revealedAt())
                .as("echoed from the saved row, so a caller sees the look was recorded rather than being told")
                .isEqualTo(Instant.parse("2026-09-08T16:41:03Z"));
    }

    @Test
    void ac17_theAuditRepositoryExposesNoWayToDeleteARow() {
        List<String> methods = java.util.Arrays.stream(ContactRevealRepository.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();

        assertThat(methods)
                .as("an append-only table should not publish delete methods at all")
                .containsExactlyInAnyOrder("save", "findByRevealedUserIdOrderByRevealedAtDesc",
                        "countByRevealedUserId");
    }

    @Test
    void ac17_theAuditEntityHasNoSetters() {
        List<String> setters = java.util.Arrays.stream(ContactReveal.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(name -> name.startsWith("set"))
                .toList();

        assertThat(setters)
                .as("events do not change their minds")
                .isEmpty();
    }

    // ---------- AC-18: the log read back to the person whose number it is ----------

    @Test
    void ac18_myRevealsReturnsWhoLookedAndCarriesNoNumber() {
        ContactReveal row = new ContactReveal(PLEDGE_ID, REQUEST_ID, REQUESTER_USER_ID,
                "Karim Ahmed", UserRole.REQUESTER, DONOR_USER_ID);
        setField(row, "id", 9L);
        setField(row, "revealedAt", Instant.parse("2026-09-08T16:41:03Z"));
        Page<ContactReveal> found = new PageImpl<>(List.of(row));

        when(reveals.findByRevealedUserIdOrderByRevealedAtDesc(eqDonor(), any())).thenReturn(found);

        PageResponse<RevealResponse> page = service.myReveals(DONOR_USER_ID, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        RevealResponse entry = page.content().get(0);
        assertThat(entry.viewer().fullName()).isEqualTo("Karim Ahmed");
        assertThat(entry.viewer().role()).isEqualTo(UserRole.REQUESTER);
        assertThat(entry.requestId()).isEqualTo(REQUEST_ID);
        assertThat(entry.pledgeId()).isEqualTo(PLEDGE_ID);
        assertThat(entry.revealedAt()).isEqualTo(Instant.parse("2026-09-08T16:41:03Z"));
    }

    @Test
    void ac18_myRevealsIsEmptyForSomebodyNobodyHasLookedUp() {
        when(reveals.findByRevealedUserIdOrderByRevealedAtDesc(eqDonor(), any())).thenReturn(Page.empty());

        PageResponse<RevealResponse> page = service.myReveals(DONOR_USER_ID, 0, 20);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    @Test
    void ac18_theCountIsAvailableWithoutPaging() {
        when(reveals.countByRevealedUserId(DONOR_USER_ID)).thenReturn(3L);

        assertThat(service.myRevealCount(DONOR_USER_ID)).isEqualTo(3L);
    }

    // ---------- fixtures ----------

    private static Long eqDonor() {
        return org.mockito.ArgumentMatchers.eq(DONOR_USER_ID);
    }

    private void givenAnAcceptedPledge() {
        Pledge pledge = pledgeInStatus(PledgeStatus.ACCEPTED);
        when(pledges.findById(PLEDGE_ID)).thenReturn(Optional.of(pledge));
        when(reveals.save(any(ContactReveal.class))).thenAnswer(call -> {
            ContactReveal row = call.getArgument(0);
            setField(row, "revealedAt", Instant.parse("2026-09-08T16:41:03Z"));
            return row;
        });
    }

    private void givenAnAcceptedPledgeWithoutStubbingTheSave() {
        Pledge pledge = pledgeInStatus(PledgeStatus.ACCEPTED);
        when(pledges.findById(PLEDGE_ID)).thenReturn(Optional.of(pledge));
    }

    private Pledge pledgeInStatus(PledgeStatus status) {
        AppUser requester = user(REQUESTER_USER_ID, "Karim Ahmed", REQUESTER_PHONE, UserRole.REQUESTER);
        AppUser donorUser = user(DONOR_USER_ID, "Rahim Uddin", DONOR_PHONE, UserRole.DONOR);

        BloodRequest request = new BloodRequest(requester, BloodGroup.B_POSITIVE, dmch(),
                (short) 2, LocalDate.of(2026, 9, 11), null);
        request.applyStatus(BloodRequestStatus.PLEDGED);
        setField(request, "id", REQUEST_ID);

        DonorProfile donor = new DonorProfile(donorUser, BloodGroup.O_NEGATIVE, chawkbazar(), null, true);
        setField(donor, "id", 12L);

        Pledge pledge = new Pledge(request, donor);
        pledge.applyStatus(status);
        setField(pledge, "id", PLEDGE_ID);
        return pledge;
    }

    private AppUser user(long id, String name, String phone, UserRole role) {
        AppUser user = new AppUser(name, phone, "$2a$10$hash", role);
        setField(user, "id", id);
        return user;
    }

    private Hospital dmch() {
        Hospital hospital = newInstance(Hospital.class);
        hospital.setName("Dhaka Medical College Hospital");
        hospital.setThana(chawkbazar());
        hospital.setLatitude(new BigDecimal("23.725800"));
        hospital.setLongitude(new BigDecimal("90.397500"));
        setField(hospital, "id", 1L);
        return hospital;
    }

    private Thana chawkbazar() {
        Thana thana = newInstance(Thana.class);
        thana.setName("Chawkbazar");
        thana.setDistrict("Dhaka");
        thana.setLatitude(new BigDecimal("23.718300"));
        thana.setLongitude(new BigDecimal("90.393600"));
        setField(thana, "id", 8L);
        return thana;
    }

    private <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException cannotHappen) {
            throw new IllegalStateException(cannotHappen);
        }
    }

    private void setField(Object entity, String name, Object value) {
        try {
            Field field = entity.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException cannotHappen) {
            throw new IllegalStateException(cannotHappen);
        }
    }
}
