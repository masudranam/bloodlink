package com.bloodlink.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bloodlink.donor.BloodGroup;
import com.bloodlink.reference.Hospital;
import com.bloodlink.reference.Thana;
import com.bloodlink.request.BloodRequest;
import com.bloodlink.request.BloodRequestRepository;
import com.bloodlink.request.BloodRequestStatus;
import com.bloodlink.user.AppUser;
import com.bloodlink.user.UserRole;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The two things about this job that are worth testing: which requests it picks,
 * and that it does not touch a status itself.
 *
 * <p>The transition table is covered exhaustively in
 * {@link BloodRequestStateMachineTest}. What matters here is that a background
 * process with no user to answer to still asks it.
 */
@ExtendWith(MockitoExtension.class)
class RequestExpiryJobTest {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final Instant NOON_IN_DHAKA = Instant.parse("2026-09-08T06:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);
    private static final long REQUESTER_ID = 5L;

    @Mock
    private BloodRequestRepository requests;

    @Mock
    private BloodRequestService requestService;

    private RequestExpiryJob job;

    @BeforeEach
    void setUp() {
        job = new RequestExpiryJob(requests, requestService, Clock.fixed(NOON_IN_DHAKA, DHAKA));
    }

    // ---------- AC-1 and AC-2: what expires ----------

    @Test
    void ac1_anOpenRequestPastItsDateIsExpired() {
        BloodRequest stale = request(41L, BloodRequestStatus.OPEN, TODAY.minusDays(1));
        when(requests.findByStatusInAndNeededByBeforeOrderByNeededByAsc(any(), eq(TODAY)))
                .thenReturn(List.of(stale));

        assertThat(job.expireStaleRequests()).isEqualTo(1);

        verify(requestService).transition(41L, BloodRequestStatus.EXPIRED);
    }

    @Test
    void ac2_theQueryAsksForBothLiveStatuses() {
        when(requests.findByStatusInAndNeededByBeforeOrderByNeededByAsc(any(), eq(TODAY)))
                .thenReturn(List.of());

        job.expireStaleRequests();

        assertThat(capturedStatuses())
                .as("somebody offering does not extend the date the blood was needed by")
                .containsExactlyInAnyOrder(BloodRequestStatus.OPEN, BloodRequestStatus.PLEDGED);
    }

    @Test
    void ac2_aPledgedRequestPastItsDateIsExpiredToo() {
        BloodRequest stale = request(42L, BloodRequestStatus.PLEDGED, TODAY.minusDays(3));
        when(requests.findByStatusInAndNeededByBeforeOrderByNeededByAsc(any(), eq(TODAY)))
                .thenReturn(List.of(stale));

        assertThat(job.expireStaleRequests()).isEqualTo(1);

        verify(requestService).transition(42L, BloodRequestStatus.EXPIRED);
    }

    // ---------- AC-3: the boundary, which is the whole point ----------

    @Test
    void ac3_theQueryUsesTodayStrictlySoARequestNeededTodaySurvives() {
        when(requests.findByStatusInAndNeededByBeforeOrderByNeededByAsc(any(), eq(TODAY)))
                .thenReturn(List.of());

        job.expireStaleRequests();

        // findBy...NeededByBefore(today) is needed_by < today. Passing today+1,
        // or using a <= comparison, would expire a request on the morning of the
        // day the blood is needed - the worst possible day to remove one.
        ArgumentCaptor<LocalDate> cutoff = ArgumentCaptor.forClass(LocalDate.class);
        verify(requests).findByStatusInAndNeededByBeforeOrderByNeededByAsc(any(), cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(TODAY);
    }

    // ---------- AC-4: nothing else is touched ----------

    @Test
    void ac4_aRunWithNoStaleRequestsTransitionsNothing() {
        when(requests.findByStatusInAndNeededByBeforeOrderByNeededByAsc(any(), eq(TODAY)))
                .thenReturn(List.of());

        assertThat(job.expireStaleRequests()).isZero();

        verifyNoInteractions(requestService);
    }

    @Test
    void ac4_terminalStatusesAreNeverEvenAskedFor() {
        when(requests.findByStatusInAndNeededByBeforeOrderByNeededByAsc(any(), eq(TODAY)))
                .thenReturn(List.of());

        job.expireStaleRequests();

        assertThat(capturedStatuses())
                .doesNotContain(BloodRequestStatus.FULFILLED, BloodRequestStatus.CANCELLED,
                        BloodRequestStatus.EXPIRED);
    }

    // ---------- AC-5: the state machine decides, even here ----------

    @Test
    void ac5_theJobOnlyEverCallsTransition() {
        BloodRequest stale = request(43L, BloodRequestStatus.OPEN, TODAY.minusDays(2));
        when(requests.findByStatusInAndNeededByBeforeOrderByNeededByAsc(any(), eq(TODAY)))
                .thenReturn(List.of(stale));

        job.expireStaleRequests();

        // No save, no applyStatus: the tempting version of this class writes an
        // UPDATE and would expire a request somebody had already fulfilled.
        verify(requests, never()).save(any());
        verify(requestService).transition(43L, BloodRequestStatus.EXPIRED);
        assertThat(stale.getStatus())
                .as("the job does not mutate the entity itself")
                .isEqualTo(BloodRequestStatus.OPEN);
    }

    @Test
    void ac5_anIllegalTransitionIsNotSwallowed() {
        BloodRequest stale = request(44L, BloodRequestStatus.OPEN, TODAY.minusDays(1));
        when(requests.findByStatusInAndNeededByBeforeOrderByNeededByAsc(any(), eq(TODAY)))
                .thenReturn(List.of(stale));
        when(requestService.transition(anyLong(), any()))
                .thenThrow(new IllegalTransitionException(
                        BloodRequestStatus.FULFILLED, BloodRequestStatus.EXPIRED));

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> job.expireStaleRequests())
                .as("a refused move means the world changed under the job, which is worth knowing")
                .isInstanceOf(IllegalTransitionException.class);
    }

    // ---------- AC-6: the clock, and the same zone as eligibility ----------

    @Test
    void ac6_todayComesFromTheInjectedClock() {
        when(requests.findByStatusInAndNeededByBeforeOrderByNeededByAsc(any(), eq(TODAY)))
                .thenReturn(List.of());

        job.expireStaleRequests();

        verify(requests).findByStatusInAndNeededByBeforeOrderByNeededByAsc(any(), eq(TODAY));
    }

    @Test
    void ac6_movingTheClockForwardMovesTheCutoff() {
        job = new RequestExpiryJob(requests, requestService,
                Clock.fixed(NOON_IN_DHAKA.plusSeconds(86_400), DHAKA));
        when(requests.findByStatusInAndNeededByBeforeOrderByNeededByAsc(any(), any()))
                .thenReturn(List.of());

        job.expireStaleRequests();

        verify(requests)
                .findByStatusInAndNeededByBeforeOrderByNeededByAsc(any(), eq(TODAY.plusDays(1)));
    }

    @Test
    void ac6_theZoneIsTheClockBeansAndNotTheServers() {
        // The Clock bean is built with the configured zone, so a job that reads
        // LocalDate.now(clock) is in Dhaka by construction. An instant that is
        // still 'yesterday' in UTC must read as today here.
        Clock lateEvening = Clock.fixed(Instant.parse("2026-09-08T19:00:00Z"), DHAKA);
        job = new RequestExpiryJob(requests, requestService, lateEvening);
        when(requests.findByStatusInAndNeededByBeforeOrderByNeededByAsc(any(), any()))
                .thenReturn(List.of());

        job.expireStaleRequests();

        verify(requests)
                .findByStatusInAndNeededByBeforeOrderByNeededByAsc(any(), eq(LocalDate.of(2026, 9, 9)));
    }

    // ---------- AC-7: idempotence ----------

    @Test
    void ac7_asecondRunFindsNothingBecauseTheFirstMovedThemAll() {
        BloodRequest stale = request(45L, BloodRequestStatus.OPEN, TODAY.minusDays(1));
        when(requests.findByStatusInAndNeededByBeforeOrderByNeededByAsc(any(), eq(TODAY)))
                .thenReturn(List.of(stale), List.of());

        assertThat(job.expireStaleRequests()).isEqualTo(1);
        assertThat(job.expireStaleRequests())
                .as("EXPIRED is not in the expirable set, so the second run has nothing to do")
                .isZero();

        verify(requestService).transition(45L, BloodRequestStatus.EXPIRED);
    }

    @Test
    void ac7_everyStaleRequestInABatchIsExpired() {
        when(requests.findByStatusInAndNeededByBeforeOrderByNeededByAsc(any(), eq(TODAY)))
                .thenReturn(List.of(
                        request(51L, BloodRequestStatus.OPEN, TODAY.minusDays(5)),
                        request(52L, BloodRequestStatus.PLEDGED, TODAY.minusDays(2)),
                        request(53L, BloodRequestStatus.OPEN, TODAY.minusDays(1))));

        assertThat(job.expireStaleRequests()).isEqualTo(3);

        verify(requestService).transition(51L, BloodRequestStatus.EXPIRED);
        verify(requestService).transition(52L, BloodRequestStatus.EXPIRED);
        verify(requestService).transition(53L, BloodRequestStatus.EXPIRED);
    }

    // ---------- fixtures ----------

    @SuppressWarnings("unchecked")
    private Collection<BloodRequestStatus> capturedStatuses() {
        ArgumentCaptor<Collection<BloodRequestStatus>> captor =
                ArgumentCaptor.forClass(Collection.class);
        verify(requests)
                .findByStatusInAndNeededByBeforeOrderByNeededByAsc(captor.capture(), any());
        return captor.getValue();
    }

    private BloodRequest request(long id, BloodRequestStatus status, LocalDate neededBy) {
        AppUser requester = new AppUser("Karim Ahmed", "+8801712345678", "$2a$10$hash",
                UserRole.REQUESTER);
        setField(requester, "id", REQUESTER_ID);

        BloodRequest request = new BloodRequest(requester, BloodGroup.B_POSITIVE, dmch(),
                (short) 2, neededBy, null);
        request.applyStatus(status);
        setField(request, "id", id);
        return request;
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
