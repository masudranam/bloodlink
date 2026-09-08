package com.bloodlink.pledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bloodlink.donor.BloodGroup;
import com.bloodlink.donor.DonorProfile;
import com.bloodlink.donor.DonorProfileRepository;
import com.bloodlink.donor.service.BloodCompatibilityService;
import com.bloodlink.donor.service.EligibilityCalculator;
import com.bloodlink.donor.service.EligibilityProperties;
import com.bloodlink.pledge.ContactResponse;
import com.bloodlink.pledge.CounterpartySummary;
import com.bloodlink.pledge.Pledge;
import com.bloodlink.pledge.PledgeDonorSummary;
import com.bloodlink.pledge.PledgeRepository;
import com.bloodlink.pledge.PledgeResponse;
import com.bloodlink.pledge.PledgeStatus;
import com.bloodlink.pledge.RevealResponse;
import com.bloodlink.pledge.ViewerSummary;
import com.bloodlink.reference.Hospital;
import com.bloodlink.reference.Thana;
import com.bloodlink.request.BloodRequest;
import com.bloodlink.request.BloodRequestStatus;
import com.bloodlink.request.service.BloodRequestService;
import com.bloodlink.request.service.NotTheRequesterException;
import com.bloodlink.request.service.RequestNotActiveException;
import com.bloodlink.user.AppUser;
import com.bloodlink.user.UserRole;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

/**
 * The guards around a pledge: who may make one, who may answer it, and what a
 * pledge does to the request behind it.
 *
 * <p>The transition table is covered exhaustively in
 * {@link PledgeStateMachineTest}, the 8x8 matrix in
 * {@code BloodCompatibilityServiceTest} and the date boundary in
 * {@code EligibilityCalculatorTest}. What is asserted here is that this service
 * consults all three rather than deciding anything itself, and that the request
 * moves in and out of PLEDGED at the right moments.
 */
@ExtendWith(MockitoExtension.class)
class PledgeServiceTest {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final Instant NOON_IN_DHAKA = Instant.parse("2026-09-08T06:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);
    private static final int INTERVAL_DAYS = 90;

    private static final long REQUESTER_ID = 5L;
    private static final long SOMEONE_ELSE_ID = 6L;
    private static final long DONOR_USER_ID = 7L;
    private static final long OTHER_DONOR_USER_ID = 8L;
    private static final long DONOR_PROFILE_ID = 12L;
    private static final long REQUEST_ID = 3L;
    private static final long PLEDGE_ID = 4L;

    @Mock
    private PledgeRepository pledges;

    @Mock
    private DonorProfileRepository profiles;

    @Mock
    private BloodRequestService requests;

    private final BloodCompatibilityService compatibility = new BloodCompatibilityService();

    private PledgeService service;

    @BeforeEach
    void setUp() {
        service = new PledgeService(pledges, profiles, requests, new PledgeStateMachine(),
                compatibility, calculatorAt(NOON_IN_DHAKA));
    }

    // ---------- AC-1: a pledge starts PENDING and lifts the request out of OPEN ----------

    @Test
    void ac1_pledge_savesAPendingPledgeAndMovesTheRequestToPledged() {
        BloodRequest request = openRequest(BloodGroup.B_POSITIVE);
        DonorProfile donor = donorProfile(BloodGroup.O_NEGATIVE, null, DONOR_USER_ID);
        givenTheDonorMayPledge(request, donor);

        PledgeResponse response = service.pledge(DONOR_USER_ID, REQUEST_ID);

        assertThat(response.status()).isEqualTo(PledgeStatus.PENDING);
        assertThat(response.requestId()).isEqualTo(REQUEST_ID);
        assertThat(response.donor().fullName()).isEqualTo("Rahim Uddin");
        assertThat(response.donor().bloodGroup()).isEqualTo(BloodGroup.O_NEGATIVE);
        assertThat(response.donor().thana().name()).isEqualTo("Chawkbazar");
        assertThat(response.hospital().name()).isEqualTo("Dhaka Medical College Hospital");
        assertThat(response.patientBloodGroup()).isEqualTo(BloodGroup.B_POSITIVE);
        assertThat(response.decidedAt())
                .as("a pending pledge has not been answered, so there is no decision to date")
                .isNull();

        verify(requests).transition(REQUEST_ID, BloodRequestStatus.PLEDGED);
    }

    @Test
    void ac1_pledge_leavesAnAlreadyPledgedRequestWhereItIs() {
        BloodRequest request = requestInStatus(BloodGroup.B_POSITIVE, BloodRequestStatus.PLEDGED);
        DonorProfile donor = donorProfile(BloodGroup.B_NEGATIVE, null, DONOR_USER_ID);
        givenTheDonorMayPledge(request, donor);

        service.pledge(DONOR_USER_ID, REQUEST_ID);

        verify(requests, never()).transition(anyLong(), any());
    }

    // ---------- AC-2: the double pledge ----------

    @Test
    void ac2_pledge_refusesASecondPledgeFromTheSameDonor() {
        BloodRequest request = openRequest(BloodGroup.B_POSITIVE);
        DonorProfile donor = donorProfile(BloodGroup.B_POSITIVE, null, DONOR_USER_ID);
        when(requests.requireActive(REQUEST_ID)).thenReturn(request);
        when(profiles.findByUserId(DONOR_USER_ID)).thenReturn(Optional.of(donor));
        when(pledges.existsByRequestIdAndDonorId(REQUEST_ID, DONOR_PROFILE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.pledge(DONOR_USER_ID, REQUEST_ID))
                .isInstanceOf(AlreadyPledgedException.class)
                .hasMessage("You have already pledged against request 3");

        verify(pledges, never()).saveAndFlush(any());
        verify(requests, never()).transition(anyLong(), any());
    }

    @Test
    void ac2_pledge_reportsTheUniqueConstraintAsAnOrdinaryDoublePledge() {
        BloodRequest request = openRequest(BloodGroup.B_POSITIVE);
        DonorProfile donor = donorProfile(BloodGroup.B_POSITIVE, null, DONOR_USER_ID);
        when(requests.requireActive(REQUEST_ID)).thenReturn(request);
        when(profiles.findByUserId(DONOR_USER_ID)).thenReturn(Optional.of(donor));
        when(pledges.existsByRequestIdAndDonorId(REQUEST_ID, DONOR_PROFILE_ID)).thenReturn(false);
        // Two concurrent pledges: both passed the check above, the index refused
        // the loser. From the caller's point of view nothing raced.
        when(pledges.saveAndFlush(any(Pledge.class)))
                .thenThrow(new DataIntegrityViolationException("uq_pledge_request_donor"));

        assertThatThrownBy(() -> service.pledge(DONOR_USER_ID, REQUEST_ID))
                .isInstanceOf(AlreadyPledgedException.class);
    }

    // ---------- AC-3: the matrix decides who may offer ----------

    static Stream<Arguments> everyPatientAndDonorGroup() {
        return Stream.of(BloodGroup.values())
                .flatMap(patient -> Stream.of(BloodGroup.values())
                        .map(donor -> Arguments.of(patient, donor)));
    }

    @ParameterizedTest(name = "[{index}] {1} donor pledging for a {0} patient")
    @MethodSource("everyPatientAndDonorGroup")
    void ac3_pledge_succeedsExactlyWhenTheMatrixAllowsTheTransfusion(BloodGroup patient, BloodGroup donorGroup) {
        BloodRequest request = openRequest(patient);
        DonorProfile donor = donorProfile(donorGroup, null, DONOR_USER_ID);
        boolean allowed = compatibility.canReceive(patient, donorGroup);

        when(requests.requireActive(REQUEST_ID)).thenReturn(request);
        when(profiles.findByUserId(DONOR_USER_ID)).thenReturn(Optional.of(donor));
        if (allowed) {
            when(pledges.existsByRequestIdAndDonorId(REQUEST_ID, DONOR_PROFILE_ID)).thenReturn(false);
            when(pledges.saveAndFlush(any(Pledge.class))).thenAnswer(call -> call.getArgument(0));
        }

        if (allowed) {
            assertThatCode(() -> service.pledge(DONOR_USER_ID, REQUEST_ID)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> service.pledge(DONOR_USER_ID, REQUEST_ID))
                    .isInstanceOf(IncompatibleBloodGroupException.class)
                    .hasMessage("A " + patient.getSymbol() + " patient cannot receive from a "
                            + donorGroup.getSymbol() + " donor");
        }
    }

    // ---------- AC-4: the eligibility boundary, seen through a pledge ----------

    @Test
    void ac4_pledge_refusesADonorWhoseIntervalHasNotFullyElapsed() {
        BloodRequest request = openRequest(BloodGroup.B_POSITIVE);
        DonorProfile donor = donorProfile(BloodGroup.B_POSITIVE, TODAY.minusDays(90), DONOR_USER_ID);
        when(requests.requireActive(REQUEST_ID)).thenReturn(request);
        when(profiles.findByUserId(DONOR_USER_ID)).thenReturn(Optional.of(donor));

        assertThatThrownBy(() -> service.pledge(DONOR_USER_ID, REQUEST_ID))
                .isInstanceOf(DonorNotEligibleException.class)
                .as("the rule is strict: 90 days elapsed is not 90 days past")
                .hasMessage("You may donate again from " + TODAY.plusDays(1));

        verify(pledges, never()).saveAndFlush(any());
    }

    @Test
    void ac4_pledge_acceptsADonorOneDayPastTheInterval() {
        BloodRequest request = openRequest(BloodGroup.B_POSITIVE);
        DonorProfile donor = donorProfile(BloodGroup.B_POSITIVE, TODAY.minusDays(91), DONOR_USER_ID);
        givenTheDonorMayPledge(request, donor);

        assertThatCode(() -> service.pledge(DONOR_USER_ID, REQUEST_ID)).doesNotThrowAnyException();
    }

    @Test
    void ac4_pledge_acceptsADonorWhoHasNeverGivenBlood() {
        BloodRequest request = openRequest(BloodGroup.B_POSITIVE);
        DonorProfile donor = donorProfile(BloodGroup.B_POSITIVE, null, DONOR_USER_ID);
        givenTheDonorMayPledge(request, donor);

        assertThatCode(() -> service.pledge(DONOR_USER_ID, REQUEST_ID)).doesNotThrowAnyException();
    }

    @Test
    void ac4_pledge_acceptsAnUnavailableDonorWhoOffersAnyway() {
        BloodRequest request = openRequest(BloodGroup.B_POSITIVE);
        DonorProfile donor = donorProfile(BloodGroup.B_POSITIVE, null, DONOR_USER_ID);
        donor.setAvailable(false);
        givenTheDonorMayPledge(request, donor);

        assertThatCode(() -> service.pledge(DONOR_USER_ID, REQUEST_ID))
                .as("availability governs search; refusing their own volunteering would overrule them")
                .doesNotThrowAnyException();
    }

    // ---------- AC-5 and AC-7: nothing to check against, and nothing to pledge for ----------

    @Test
    void ac5_pledge_refusesADonorWithNoProfile() {
        when(requests.requireActive(REQUEST_ID)).thenReturn(openRequest(BloodGroup.B_POSITIVE));
        when(profiles.findByUserId(DONOR_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pledge(DONOR_USER_ID, REQUEST_ID))
                .isInstanceOf(DonorProfileRequiredException.class);

        verify(pledges, never()).saveAndFlush(any());
    }

    @Test
    void ac7_pledge_refusesARequestThatIsOver() {
        when(requests.requireActive(REQUEST_ID))
                .thenThrow(new RequestNotActiveException(BloodRequestStatus.CANCELLED));

        assertThatThrownBy(() -> service.pledge(DONOR_USER_ID, REQUEST_ID))
                .isInstanceOf(RequestNotActiveException.class)
                .hasMessage("A request that is CANCELLED is no longer looking for donors");

        verifyNoInteractions(pledges);
        verifyNoInteractions(profiles);
    }

    // ---------- AC-8 and AC-9: reading pledges ----------

    @Test
    void ac8_forRequest_checksOwnershipBeforeReadingAnything() {
        when(requests.requireOwnedBy(SOMEONE_ELSE_ID, REQUEST_ID)).thenThrow(new NotTheRequesterException());

        assertThatThrownBy(() -> service.forRequest(SOMEONE_ELSE_ID, REQUEST_ID, 0, 20))
                .isInstanceOf(NotTheRequesterException.class);

        verifyNoInteractions(pledges);
    }

    @Test
    void ac8_forRequest_returnsThePledgesOnThatRequest() {
        BloodRequest request = requestInStatus(BloodGroup.B_POSITIVE, BloodRequestStatus.PLEDGED);
        Pledge pledge = pendingPledge(request, donorProfile(BloodGroup.O_NEGATIVE, null, DONOR_USER_ID));
        Page<Pledge> found = new PageImpl<>(List.of(pledge));

        when(requests.requireOwnedBy(REQUESTER_ID, REQUEST_ID)).thenReturn(request);
        when(pledges.findByRequestIdOrderByCreatedAtDesc(eq(REQUEST_ID), any())).thenReturn(found);

        var page = service.forRequest(REQUESTER_ID, REQUEST_ID, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().get(0).donor().fullName()).isEqualTo("Rahim Uddin");
    }

    @Test
    void ac9_forDonor_readsByTheirOwnProfileId() {
        BloodRequest request = requestInStatus(BloodGroup.B_POSITIVE, BloodRequestStatus.PLEDGED);
        DonorProfile donor = donorProfile(BloodGroup.O_NEGATIVE, null, DONOR_USER_ID);
        Page<Pledge> found = new PageImpl<>(List.of(pendingPledge(request, donor)));

        when(profiles.findByUserId(DONOR_USER_ID)).thenReturn(Optional.of(donor));
        when(pledges.findByDonorIdOrderByCreatedAtDesc(eq(DONOR_PROFILE_ID), any())).thenReturn(found);

        var page = service.forDonor(DONOR_USER_ID, 0, 20);

        assertThat(page.content()).hasSize(1);
        verify(pledges).findByDonorIdOrderByCreatedAtDesc(eq(DONOR_PROFILE_ID), any());
    }

    // ---------- AC-10 to AC-12: answering a pledge ----------

    @Test
    void ac10_accept_movesThePledgeAndLeavesTheRequestPledged() {
        Pledge pledge = pendingPledgeOnAPledgedRequest();
        givenThePledgeExists(pledge);
        when(pledges.countByRequestIdAndStatusIn(eq(REQUEST_ID), any())).thenReturn(1L);

        PledgeResponse response = service.accept(REQUESTER_ID, PLEDGE_ID);

        assertThat(response.status()).isEqualTo(PledgeStatus.ACCEPTED);
        assertThat(response.decidedAt())
                .as("an answered pledge has a decision to date")
                .isNotNull();
        verify(requests, never()).transition(anyLong(), any());
    }

    @Test
    void ac11_accept_refusesAPledgeThatIsAlreadyAccepted() {
        Pledge pledge = pendingPledgeOnAPledgedRequest();
        pledge.applyStatus(PledgeStatus.ACCEPTED);
        givenThePledgeExists(pledge);

        assertThatThrownBy(() -> service.accept(REQUESTER_ID, PLEDGE_ID))
                .isInstanceOf(IllegalPledgeTransitionException.class)
                .hasMessage("A pledge cannot move from ACCEPTED to ACCEPTED");

        verify(pledges, never()).save(any());
    }

    @Test
    void ac11_accept_refusesAWithdrawnPledge() {
        Pledge pledge = pendingPledgeOnAPledgedRequest();
        pledge.applyStatus(PledgeStatus.WITHDRAWN);
        givenThePledgeExists(pledge);

        assertThatThrownBy(() -> service.accept(REQUESTER_ID, PLEDGE_ID))
                .isInstanceOf(IllegalPledgeTransitionException.class)
                .hasMessage("A pledge cannot move from WITHDRAWN to ACCEPTED");
    }

    @Test
    void ac12_accept_refusesSomebodyElsesRequest() {
        givenThePledgeExists(pendingPledgeOnAPledgedRequest());

        assertThatThrownBy(() -> service.accept(SOMEONE_ELSE_ID, PLEDGE_ID))
                .isInstanceOf(NotTheRequesterException.class);

        verify(pledges, never()).save(any());
    }

    @Test
    void ac12_withdraw_refusesAnotherDonorsPledge() {
        givenThePledgeExists(pendingPledgeOnAPledgedRequest());

        assertThatThrownBy(() -> service.withdraw(OTHER_DONOR_USER_ID, PLEDGE_ID))
                .isInstanceOf(NotThePledgingDonorException.class);

        verify(pledges, never()).save(any());
    }

    @Test
    void ac12_withdraw_refusesTheRequesterToo() {
        givenThePledgeExists(pendingPledgeOnAPledgedRequest());

        assertThatThrownBy(() -> service.withdraw(REQUESTER_ID, PLEDGE_ID))
                .as("a requester who wants rid of a pledge declines it; withdrawing is the donor's word")
                .isInstanceOf(NotThePledgingDonorException.class);
    }

    @Test
    void ac12_withdraw_checksOwnershipBeforeTheTransition() {
        Pledge pledge = pendingPledgeOnAPledgedRequest();
        pledge.applyStatus(PledgeStatus.DECLINED);
        givenThePledgeExists(pledge);

        assertThatThrownBy(() -> service.withdraw(OTHER_DONOR_USER_ID, PLEDGE_ID))
                .as("403 before 409: an error must not report on somebody else's arrangements")
                .isInstanceOf(NotThePledgingDonorException.class);
    }

    // ---------- AC-13 and AC-14: the request goes back to the feed ----------

    @Test
    void ac13_decline_returnsTheRequestToOpenWhenNoActivePledgeIsLeft() {
        Pledge pledge = pendingPledgeOnAPledgedRequest();
        givenThePledgeExists(pledge);
        when(pledges.countByRequestIdAndStatusIn(eq(REQUEST_ID), any())).thenReturn(0L);

        PledgeResponse response = service.decline(REQUESTER_ID, PLEDGE_ID);

        assertThat(response.status()).isEqualTo(PledgeStatus.DECLINED);
        verify(requests).transition(REQUEST_ID, BloodRequestStatus.OPEN);
    }

    @Test
    void ac14_withdraw_returnsTheRequestToOpenWhenNoActivePledgeIsLeft() {
        Pledge pledge = pendingPledgeOnAPledgedRequest();
        givenThePledgeExists(pledge);
        when(pledges.countByRequestIdAndStatusIn(eq(REQUEST_ID), any())).thenReturn(0L);

        service.withdraw(DONOR_USER_ID, PLEDGE_ID);

        verify(requests).transition(REQUEST_ID, BloodRequestStatus.OPEN);
    }

    @Test
    void ac14_withdraw_leavesTheRequestPledgedWhileAnotherPledgeStands() {
        Pledge pledge = pendingPledgeOnAPledgedRequest();
        givenThePledgeExists(pledge);
        when(pledges.countByRequestIdAndStatusIn(eq(REQUEST_ID), any())).thenReturn(1L);

        service.withdraw(DONOR_USER_ID, PLEDGE_ID);

        verify(requests, never()).transition(anyLong(), any());
    }

    @Test
    void ac14_theActiveStatusesAreTheOnesCounted() {
        Pledge pledge = pendingPledgeOnAPledgedRequest();
        givenThePledgeExists(pledge);
        when(pledges.countByRequestIdAndStatusIn(eq(REQUEST_ID), any())).thenReturn(0L);

        service.decline(REQUESTER_ID, PLEDGE_ID);

        var statuses = org.mockito.ArgumentCaptor.<java.util.Collection<PledgeStatus>>captor();
        verify(pledges).countByRequestIdAndStatusIn(eq(REQUEST_ID), statuses.capture());
        assertThat(statuses.getValue())
                .containsExactlyInAnyOrder(PledgeStatus.PENDING, PledgeStatus.ACCEPTED);
    }

    // ---------- AC-19: exactly one type in the project carries a phone number ----------

    @Test
    void ac19_noPledgeResponseTypeDeclaresAPhoneField() {
        for (Class<?> type : List.of(PledgeResponse.class, PledgeDonorSummary.class,
                RevealResponse.class, ViewerSummary.class, ContactResponse.class)) {
            assertThat(componentNames(type))
                    .as("%s must not be able to carry a phone number", type.getSimpleName())
                    .noneMatch(name -> name.toLowerCase(Locale.ROOT).contains("phone"));
        }
    }

    @Test
    void ac19_counterpartySummaryIsTheOnlyTypeThatCarriesAPhoneNumber() {
        assertThat(componentNames(CounterpartySummary.class))
                .as("asserted positively, so this test documents where the one number lives")
                .contains("phone");
    }

    // ---------- fixtures ----------

    private EligibilityCalculator calculatorAt(Instant instant) {
        return new EligibilityCalculator(Clock.fixed(instant, DHAKA),
                new EligibilityProperties(INTERVAL_DAYS, DHAKA));
    }

    private void givenTheDonorMayPledge(BloodRequest request, DonorProfile donor) {
        when(requests.requireActive(REQUEST_ID)).thenReturn(request);
        when(profiles.findByUserId(DONOR_USER_ID)).thenReturn(Optional.of(donor));
        when(pledges.existsByRequestIdAndDonorId(REQUEST_ID, DONOR_PROFILE_ID)).thenReturn(false);
        when(pledges.saveAndFlush(any(Pledge.class))).thenAnswer(call -> call.getArgument(0));
    }

    private void givenThePledgeExists(Pledge pledge) {
        when(pledges.findById(PLEDGE_ID)).thenReturn(Optional.of(pledge));
    }

    private Pledge pendingPledgeOnAPledgedRequest() {
        BloodRequest request = requestInStatus(BloodGroup.B_POSITIVE, BloodRequestStatus.PLEDGED);
        return pendingPledge(request, donorProfile(BloodGroup.O_NEGATIVE, null, DONOR_USER_ID));
    }

    private Pledge pendingPledge(BloodRequest request, DonorProfile donor) {
        Pledge pledge = new Pledge(request, donor);
        setId(pledge, PLEDGE_ID);
        setField(pledge, "createdAt", Instant.parse("2026-09-08T10:00:00Z"));
        setField(pledge, "updatedAt", Instant.parse("2026-09-08T11:00:00Z"));
        return pledge;
    }

    private BloodRequest openRequest(BloodGroup patientGroup) {
        return requestInStatus(patientGroup, BloodRequestStatus.OPEN);
    }

    private BloodRequest requestInStatus(BloodGroup patientGroup, BloodRequestStatus status) {
        BloodRequest request = new BloodRequest(
                user(REQUESTER_ID, "Karim Ahmed", UserRole.REQUESTER),
                patientGroup, dmch(), (short) 2, TODAY.plusDays(3), null);
        request.applyStatus(status);
        setId(request, REQUEST_ID);
        return request;
    }

    private DonorProfile donorProfile(BloodGroup group, LocalDate lastDonationDate, long userId) {
        DonorProfile profile = new DonorProfile(
                user(userId, "Rahim Uddin", UserRole.DONOR), group, chawkbazar(), lastDonationDate, true);
        setId(profile, DONOR_PROFILE_ID);
        return profile;
    }

    private AppUser user(long id, String name, UserRole role) {
        AppUser user = new AppUser(name, "+880171200000" + id, "$2a$10$hash", role);
        setId(user, id);
        return user;
    }

    private Hospital dmch() {
        Hospital hospital = newInstance(Hospital.class);
        hospital.setName("Dhaka Medical College Hospital");
        hospital.setThana(chawkbazar());
        hospital.setLatitude(new BigDecimal("23.725800"));
        hospital.setLongitude(new BigDecimal("90.397500"));
        setId(hospital, 1L);
        return hospital;
    }

    private Thana chawkbazar() {
        Thana thana = newInstance(Thana.class);
        thana.setName("Chawkbazar");
        thana.setDistrict("Dhaka");
        thana.setLatitude(new BigDecimal("23.718300"));
        thana.setLongitude(new BigDecimal("90.393600"));
        setId(thana, 8L);
        return thana;
    }

    private static Stream<String> componentNames(Class<?> record) {
        return Stream.of(record.getRecordComponents()).map(RecordComponent::getName);
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

    private void setId(Object entity, long id) {
        setField(entity, "id", id);
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
