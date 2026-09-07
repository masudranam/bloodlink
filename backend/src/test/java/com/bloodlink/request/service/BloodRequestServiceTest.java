package com.bloodlink.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bloodlink.donor.BloodGroup;
import com.bloodlink.reference.Hospital;
import com.bloodlink.reference.HospitalRepository;
import com.bloodlink.reference.Thana;
import com.bloodlink.request.BloodRequest;
import com.bloodlink.request.BloodRequestRepository;
import com.bloodlink.request.BloodRequestResponse;
import com.bloodlink.request.BloodRequestStatus;
import com.bloodlink.request.CreateBloodRequest;
import com.bloodlink.user.AppUser;
import com.bloodlink.user.AppUserRepository;
import com.bloodlink.user.UserRole;
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
 * The guards around the lifecycle: who may move a request, and from where.
 *
 * <p>The transition table itself is covered exhaustively in
 * {@link BloodRequestStateMachineTest}. What is tested here is that this service
 * actually consults it, and that ownership is checked before it does.
 */
@ExtendWith(MockitoExtension.class)
class BloodRequestServiceTest {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final Instant NOON_IN_DHAKA = Instant.parse("2026-09-04T06:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 4);
    private static final long REQUESTER_ID = 3L;
    private static final long SOMEONE_ELSE_ID = 4L;
    private static final long HOSPITAL_ID = 1L;
    private static final long REQUEST_ID = 10L;

    @Mock
    private BloodRequestRepository requests;

    @Mock
    private AppUserRepository users;

    @Mock
    private HospitalRepository hospitals;

    private BloodRequestService service;

    @BeforeEach
    void setUp() {
        service = new BloodRequestService(
                requests, users, hospitals,
                new BloodRequestStateMachine(),
                Clock.fixed(NOON_IN_DHAKA, DHAKA));
    }

    // ---------- AC-1: a request starts OPEN and can start no other way ----------

    @Test
    void ac1_create_savesAnOpenRequest() {
        when(users.findById(REQUESTER_ID)).thenReturn(Optional.of(requester(REQUESTER_ID)));
        when(hospitals.findById(HOSPITAL_ID)).thenReturn(Optional.of(dmch()));
        when(requests.save(any(BloodRequest.class))).thenAnswer(call -> call.getArgument(0));

        BloodRequestResponse response = service.create(REQUESTER_ID, new CreateBloodRequest(
                BloodGroup.B_POSITIVE, HOSPITAL_ID, 2, TODAY.plusDays(3), "Ward 4"));

        ArgumentCaptor<BloodRequest> saved = ArgumentCaptor.forClass(BloodRequest.class);
        verify(requests).save(saved.capture());

        assertThat(saved.getValue().getStatus()).isEqualTo(BloodRequestStatus.OPEN);
        assertThat(response.status()).isEqualTo(BloodRequestStatus.OPEN);
        assertThat(response.hospital().name()).isEqualTo("Dhaka Medical College Hospital");
        assertThat(response.requester().fullName()).isEqualTo("Requester Three");
        assertThat(response.unitsNeeded()).isEqualTo(2);
    }

    // ---------- AC-3: well-formed but wrong ----------

    @Test
    void ac3_create_rejectsAnUnknownHospital() {
        when(users.findById(REQUESTER_ID)).thenReturn(Optional.of(requester(REQUESTER_ID)));
        when(hospitals.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(REQUESTER_ID, new CreateBloodRequest(
                BloodGroup.O_NEGATIVE, 999L, 1, TODAY, null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("no such hospital");

        verify(requests, never()).save(any());
    }

    @Test
    void ac3_create_rejectsADateThatHasPassed() {
        when(users.findById(REQUESTER_ID)).thenReturn(Optional.of(requester(REQUESTER_ID)));
        when(hospitals.findById(HOSPITAL_ID)).thenReturn(Optional.of(dmch()));

        assertThatThrownBy(() -> service.create(REQUESTER_ID, new CreateBloodRequest(
                BloodGroup.O_NEGATIVE, HOSPITAL_ID, 1, TODAY.minusDays(1), null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("must not be in the past");

        verify(requests, never()).save(any());
    }

    @Test
    void ac3_create_acceptsTodayAsTheNeededByDate() {
        when(users.findById(REQUESTER_ID)).thenReturn(Optional.of(requester(REQUESTER_ID)));
        when(hospitals.findById(HOSPITAL_ID)).thenReturn(Optional.of(dmch()));
        when(requests.save(any(BloodRequest.class))).thenAnswer(call -> call.getArgument(0));

        BloodRequestResponse response = service.create(REQUESTER_ID, new CreateBloodRequest(
                BloodGroup.A_POSITIVE, HOSPITAL_ID, 1, TODAY, null));

        assertThat(response.neededBy()).isEqualTo(TODAY);
    }

    // ---------- AC-6, AC-8, AC-9: the guards, through the service ----------

    @Test
    void ac6_cancel_movesAnOpenRequestToCancelled() {
        BloodRequest open = requestOwnedBy(REQUESTER_ID, BloodRequestStatus.OPEN);
        when(requests.findById(REQUEST_ID)).thenReturn(Optional.of(open));
        when(requests.save(any(BloodRequest.class))).thenAnswer(call -> call.getArgument(0));

        BloodRequestResponse response = service.cancel(REQUESTER_ID, REQUEST_ID);

        assertThat(response.status()).isEqualTo(BloodRequestStatus.CANCELLED);
    }

    @Test
    void ac8_fulfil_refusesAnOpenRequest() {
        when(requests.findById(REQUEST_ID))
                .thenReturn(Optional.of(requestOwnedBy(REQUESTER_ID, BloodRequestStatus.OPEN)));

        assertThatThrownBy(() -> service.fulfil(REQUESTER_ID, REQUEST_ID))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessage("A request cannot move from OPEN to FULFILLED");

        verify(requests, never()).save(any());
    }

    @Test
    void ac8_fulfil_acceptsAPledgedRequest() {
        when(requests.findById(REQUEST_ID))
                .thenReturn(Optional.of(requestOwnedBy(REQUESTER_ID, BloodRequestStatus.PLEDGED)));
        when(requests.save(any(BloodRequest.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(service.fulfil(REQUESTER_ID, REQUEST_ID).status())
                .isEqualTo(BloodRequestStatus.FULFILLED);
    }

    @Test
    void ac9_cancel_refusesARequestThatIsAlreadyCancelled() {
        when(requests.findById(REQUEST_ID))
                .thenReturn(Optional.of(requestOwnedBy(REQUESTER_ID, BloodRequestStatus.CANCELLED)));

        assertThatThrownBy(() -> service.cancel(REQUESTER_ID, REQUEST_ID))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessage("A request cannot move from CANCELLED to CANCELLED");
    }

    @Test
    void ac9_cancel_refusesAFulfilledRequest() {
        when(requests.findById(REQUEST_ID))
                .thenReturn(Optional.of(requestOwnedBy(REQUESTER_ID, BloodRequestStatus.FULFILLED)));

        assertThatThrownBy(() -> service.cancel(REQUESTER_ID, REQUEST_ID))
                .isInstanceOf(IllegalTransitionException.class);
        verify(requests, never()).save(any());
    }

    // ---------- AC-7: ownership is checked before the transition ----------

    @Test
    void ac7_cancel_refusesSomebodyElsesRequest() {
        when(requests.findById(REQUEST_ID))
                .thenReturn(Optional.of(requestOwnedBy(REQUESTER_ID, BloodRequestStatus.OPEN)));

        assertThatThrownBy(() -> service.cancel(SOMEONE_ELSE_ID, REQUEST_ID))
                .isInstanceOf(NotTheRequesterException.class);

        verify(requests, never()).save(any());
    }

    @Test
    void ac7_ownershipIsCheckedBeforeTheTransition() {
        // A terminal request owned by someone else must report the ownership
        // failure, not the transition failure: otherwise the error tells a
        // stranger what state a request they cannot touch is in.
        when(requests.findById(REQUEST_ID))
                .thenReturn(Optional.of(requestOwnedBy(REQUESTER_ID, BloodRequestStatus.FULFILLED)));

        assertThatThrownBy(() -> service.cancel(SOMEONE_ELSE_ID, REQUEST_ID))
                .isInstanceOf(NotTheRequesterException.class);
    }

    // ---------- AC-5: missing requests ----------

    @Test
    void ac5_get_failsForARequestThatDoesNotExist() {
        when(requests.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(REQUEST_ID))
                .isInstanceOf(RequestNotFoundException.class)
                .hasMessageContaining("10");
    }

    // ---------- the door for SPEC-008 and SPEC-010 ----------

    @Test
    void ac10_transition_hasNoOwnershipCheckButStillObeysTheStateMachine() {
        when(requests.findById(REQUEST_ID))
                .thenReturn(Optional.of(requestOwnedBy(REQUESTER_ID, BloodRequestStatus.OPEN)));
        when(requests.save(any(BloodRequest.class))).thenAnswer(call -> call.getArgument(0));

        // A donor pledging is not the requester, and must still be able to move it.
        assertThat(service.transition(REQUEST_ID, BloodRequestStatus.PLEDGED).status())
                .isEqualTo(BloodRequestStatus.PLEDGED);
    }

    @Test
    void ac10_transition_refusesAnIllegalMoveEvenWithoutAnOwner() {
        when(requests.findById(REQUEST_ID))
                .thenReturn(Optional.of(requestOwnedBy(REQUESTER_ID, BloodRequestStatus.EXPIRED)));

        assertThatThrownBy(() -> service.transition(REQUEST_ID, BloodRequestStatus.PLEDGED))
                .isInstanceOf(IllegalTransitionException.class);
    }

    // ---------- AC-13: no phone anywhere in the response types ----------

    @Test
    void ac13_noResponseTypeCarriesAPhoneField() {
        assertThat(BloodRequestResponse.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("phone"));
        assertThat(com.bloodlink.request.RequesterSummary.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("phone"));
        assertThat(com.bloodlink.request.HospitalSummary.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("phone"));
    }

    // ---------- fixtures ----------

    private AppUser requester(long id) {
        AppUser user = new AppUser("Requester Three", "+8801712345678", "$2a$10$hash", UserRole.REQUESTER);
        setId(user, id);
        return user;
    }

    private Hospital dmch() {
        Hospital hospital = newInstance(Hospital.class);
        hospital.setName("Dhaka Medical College Hospital");
        hospital.setThana(chawkbazar());
        hospital.setLatitude(new BigDecimal("23.725800"));
        hospital.setLongitude(new BigDecimal("90.397500"));
        setId(hospital, HOSPITAL_ID);
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

    private BloodRequest requestOwnedBy(long requesterId, BloodRequestStatus status) {
        BloodRequest request = new BloodRequest(
                requester(requesterId), BloodGroup.B_POSITIVE, dmch(),
                (short) 2, TODAY.plusDays(2), null);
        request.applyStatus(status);
        setId(request, REQUEST_ID);
        return request;
    }

    /**
     * Entity constructors are protected for JPA and ids are database-generated, so
     * test doubles need reflection rather than setters production code has no
     * reason to expose.
     *
     * @param type the entity type
     * @param <T>  the entity type
     * @return a new instance
     */
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
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException cannotHappen) {
            throw new IllegalStateException(cannotHappen);
        }
    }
}
