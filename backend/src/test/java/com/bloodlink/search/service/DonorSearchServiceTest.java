package com.bloodlink.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bloodlink.donor.BloodGroup;
import com.bloodlink.donor.service.BloodCompatibilityService;
import com.bloodlink.donor.service.EligibilityCalculator;
import com.bloodlink.donor.service.EligibilityProperties;
import com.bloodlink.reference.Hospital;
import com.bloodlink.reference.Thana;
import com.bloodlink.request.BloodRequest;
import com.bloodlink.request.BloodRequestStatus;
import com.bloodlink.request.PageResponse;
import com.bloodlink.request.service.BloodRequestService;
import com.bloodlink.request.service.NotTheRequesterException;
import com.bloodlink.request.service.RequestNotActiveException;
import com.bloodlink.request.service.RequestNotFoundException;
import com.bloodlink.search.DonorMatch;
import com.bloodlink.search.DonorMatchRow;
import com.bloodlink.search.DonorSearchRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * What this service is responsible for, and nothing else.
 *
 * <p>The three pillars it composes are tested where they live:
 * {@code BloodCompatibilityServiceTest} owns the 8x8 matrix and
 * {@code EligibilityCalculatorTest} owns the date boundary. What is asserted
 * here is that this service actually asks them rather than deciding for itself,
 * and that the query it hands the database says what the spec says it says.
 *
 * <p>Whether Postgres then filters and sorts correctly is not something a mock
 * can answer. That is verified by hand against a seeded database, and the
 * transcript is in the pull request.
 */
@ExtendWith(MockitoExtension.class)
class DonorSearchServiceTest {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final Instant NOON_IN_DHAKA = Instant.parse("2026-09-08T06:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);
    private static final int INTERVAL_DAYS = 90;
    private static final long REQUESTER_ID = 5L;
    private static final long REQUEST_ID = 2L;

    @Mock
    private BloodRequestService requests;

    @Mock
    private DonorSearchRepository donors;

    private final BloodCompatibilityService compatibility = new BloodCompatibilityService();

    private DonorSearchService service;

    @BeforeEach
    void setUp() {
        service = new DonorSearchService(requests, donors, compatibility, calculatorAt(NOON_IN_DHAKA));
    }

    // ---------- AC-1: a page of matches, nearest first ----------

    @Test
    void ac1_search_returnsTheMappedPageAndTheFullTotal() {
        givenALiveRequestFor(BloodGroup.B_POSITIVE);
        // One row on a page of size 1 out of three matches: totalElements must
        // report every match, not the size of the slice.
        Page<DonorMatchRow> slice = new PageImpl<>(
                List.of(row(12L, "Rahim Uddin", "O-", 0.0, null)), PageRequest.of(0, 1), 3);

        when(donors.search(anyList(), any(), anyInt(), anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(slice);

        PageResponse<DonorMatch> page = service.search(REQUESTER_ID, REQUEST_ID, 10, 0, 1);

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.content()).hasSize(1);

        DonorMatch match = page.content().get(0);
        assertThat(match.donorId()).isEqualTo(12L);
        assertThat(match.fullName()).isEqualTo("Rahim Uddin");
        assertThat(match.bloodGroup()).isEqualTo(BloodGroup.O_NEGATIVE);
        assertThat(match.thana().name()).isEqualTo("Chawkbazar");
        assertThat(match.distanceKm()).isEqualTo(0.0);
        assertThat(match.lastDonationDate()).isNull();
        assertThat(match.nextEligibleDate())
                .as("a donor who has never given has no next date to count from")
                .isNull();
    }

    @Test
    void ac1_search_passesTheRequestedPageThrough() {
        givenALiveRequestFor(BloodGroup.B_POSITIVE);
        givenNoMatches();

        service.search(REQUESTER_ID, REQUEST_ID, 25, 2, 15);

        assertThat(capturedPageable()).isEqualTo(PageRequest.of(2, 15));
        assertThat(capturedRadiusKm()).isEqualTo(25);
    }

    // ---------- AC-2 and AC-3: the matrix decides, and it is not restated in SQL ----------

    @Test
    void ac2_aBPositivePatientSearchesForExactlyFourGroups() {
        givenALiveRequestFor(BloodGroup.B_POSITIVE);
        givenNoMatches();

        service.search(REQUESTER_ID, REQUEST_ID, 10, 0, 20);

        assertThat(capturedBloodGroups()).containsExactlyInAnyOrder("B+", "B-", "O+", "O-");
    }

    @ParameterizedTest
    @EnumSource(BloodGroup.class)
    void ac3_theGroupsSearchedForComeFromTheCompatibilityMatrix(BloodGroup patient) {
        givenALiveRequestFor(patient);
        givenNoMatches();

        service.search(REQUESTER_ID, REQUEST_ID, 10, 0, 20);

        Set<String> expected = compatibility.compatibleDonorsFor(patient).stream()
                .map(BloodGroup::getSymbol)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertThat(capturedBloodGroups())
                .as("%s must search for exactly what the matrix allows", patient)
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void ac3_theQueryNeverWritesOutABloodGroup() {
        for (BloodGroup group : BloodGroup.values()) {
            assertThat(DonorSearchRepository.SEARCH)
                    .as("a hand-written group list would diverge from the tested matrix")
                    .doesNotContain("'" + group.getSymbol() + "'");
        }
        assertThat(DonorSearchRepository.SEARCH).contains(":bloodGroups");
    }

    // ---------- AC-4: the eligibility predicate, and the interval it uses ----------

    @Test
    void ac4_theConfiguredIntervalIsWhatTheQueryFiltersOn() {
        givenALiveRequestFor(BloodGroup.B_POSITIVE);
        givenNoMatches();

        service.search(REQUESTER_ID, REQUEST_ID, 10, 0, 20);

        assertThat(capturedIntervalDays()).isEqualTo(INTERVAL_DAYS);
    }

    @Test
    void ac4_theQueryAsksForStrictlyElapsedIntervalsAndKeepsNeverGivenDonors() {
        assertThat(DonorSearchRepository.SEARCH)
                .contains("dp.last_donation_date is null")
                .contains("< cast(:today as date)");
    }

    // ---------- AC-5: today comes from the clock, not the database ----------

    @Test
    void ac5_todayComesFromTheConfiguredClockInDhaka() {
        givenALiveRequestFor(BloodGroup.B_POSITIVE);
        givenNoMatches();

        service.search(REQUESTER_ID, REQUEST_ID, 10, 0, 20);

        assertThat(capturedToday()).isEqualTo(TODAY);
    }

    @Test
    void ac5_movingTheClockMovesToday() {
        service = new DonorSearchService(requests, donors, compatibility,
                calculatorAt(NOON_IN_DHAKA.plusSeconds(86_400)));
        givenALiveRequestFor(BloodGroup.B_POSITIVE);
        givenNoMatches();

        service.search(REQUESTER_ID, REQUEST_ID, 10, 0, 20);

        assertThat(capturedToday())
                .as("the same data one day later is a different answer, which is pillar 2")
                .isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void ac5_theQueryContainsNoCurrentDate() {
        assertThat(DonorSearchRepository.SEARCH)
                .as("asking the database for the date would leave Asia/Dhaka behind")
                .doesNotContain("current_date")
                .doesNotContain("now()")
                .contains(":today");
    }

    // ---------- AC-6 and AC-7: radius, and the fourth filter ----------

    @Test
    void ac6_theRadiusIsAppliedToAnUnroundedHaversineDistance() {
        assertThat(DonorSearchRepository.SEARCH)
                .contains("6371")
                .contains("asin(sqrt(")
                .contains("m.\"distanceKm\" <= cast(:radiusKm as double precision)");
    }

    @Test
    void ac7_unavailableDonorsAreExcludedByTheQueryItself() {
        assertThat(DonorSearchRepository.SEARCH).contains("dp.available = true");
        assertThat(DonorSearchRepository.SEARCH_COUNT)
                .as("a count that filters differently from the page it counts makes paging lie")
                .contains("dp.available = true");
    }

    // ---------- AC-8: the sort is total, so paging cannot repeat or skip ----------

    @Test
    void ac8_theSortHasThreeKeysInOrder() {
        assertThat(DonorSearchRepository.SEARCH).contains(
                "order by m.\"distanceKm\", m.\"lastDonationDate\" asc nulls first, m.\"donorId\"");
    }

    @Test
    void ac8_theQuerySortsAndThePageableDoesNot() {
        givenALiveRequestFor(BloodGroup.B_POSITIVE);
        givenNoMatches();

        service.search(REQUESTER_ID, REQUEST_ID, 10, 0, 20);

        assertThat(capturedPageable().getSort().isSorted())
                .as("a Sort here would append a second, conflicting order by")
                .isFalse();
    }

    // ---------- AC-9: one decimal, because a centroid is a kilometre coarse ----------

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.04, 1.4372, 1.45, 9.99, 12.349, 49.96})
    void ac9_distanceIsRoundedToOneDecimal(double raw) {
        double rounded = DonorSearchService.roundToOneDecimal(raw);

        assertThat(rounded * 10).isCloseTo(Math.round(rounded * 10), org.assertj.core.data.Offset.offset(1e-9));
        assertThat(rounded).isCloseTo(raw, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    void ac9_roundingIsNotTruncation() {
        assertThat(DonorSearchService.roundToOneDecimal(1.4372)).isEqualTo(1.4);
        assertThat(DonorSearchService.roundToOneDecimal(1.46)).isEqualTo(1.5);
        assertThat(DonorSearchService.roundToOneDecimal(0.0)).isEqualTo(0.0);
    }

    @Test
    void ac9_theRadiusFilterSeesTheUnroundedDistance() {
        givenALiveRequestFor(BloodGroup.B_POSITIVE);
        Page<DonorMatchRow> justOverTen =
                new PageImpl<>(List.of(row(9L, "Edge Case", "O+", 10.04, TODAY.minusDays(200))));

        when(donors.search(anyList(), any(), anyInt(), anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(justOverTen);

        DonorMatch match = service.search(REQUESTER_ID, REQUEST_ID, 10, 0, 20).content().get(0);

        assertThat(match.distanceKm())
                .as("rounding in SQL would have let 10.04 km into a 10 km search")
                .isEqualTo(10.0);
    }

    // ---------- AC-12 and AC-13: the guards belong to the request, not to search ----------

    @Test
    void ac12_aRequestThatDoesNotExistIsNotSearched() {
        when(requests.requireActiveAndOwnedBy(REQUESTER_ID, REQUEST_ID))
                .thenThrow(new RequestNotFoundException(REQUEST_ID));

        assertThatThrownBy(() -> service.search(REQUESTER_ID, REQUEST_ID, 10, 0, 20))
                .isInstanceOf(RequestNotFoundException.class);

        verifyNoInteractions(donors);
    }

    @Test
    void ac12_somebodyElsesRequestIsNotSearched() {
        when(requests.requireActiveAndOwnedBy(REQUESTER_ID, REQUEST_ID))
                .thenThrow(new NotTheRequesterException());

        assertThatThrownBy(() -> service.search(REQUESTER_ID, REQUEST_ID, 10, 0, 20))
                .isInstanceOf(NotTheRequesterException.class);

        verifyNoInteractions(donors);
    }

    @ParameterizedTest
    @EnumSource(value = BloodRequestStatus.class, names = {"FULFILLED", "CANCELLED", "EXPIRED"})
    void ac13_aTerminalRequestIsNotSearched(BloodRequestStatus terminal) {
        when(requests.requireActiveAndOwnedBy(REQUESTER_ID, REQUEST_ID))
                .thenThrow(new RequestNotActiveException(terminal));

        assertThatThrownBy(() -> service.search(REQUESTER_ID, REQUEST_ID, 10, 0, 20))
                .isInstanceOf(RequestNotActiveException.class)
                .hasMessage("A request that is " + terminal + " is no longer looking for donors");

        verifyNoInteractions(donors);
    }

    // ---------- AC-14: the privacy rule, asserted structurally ----------

    @Test
    void ac14_noSearchTypeCanCarryAPhoneNumber() {
        assertThat(accessorNames(DonorMatchRow.class))
                .as("the projection is the last line of defence: an entity here would be one getter from a phone")
                .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("phone"));

        assertThat(componentNames(DonorMatch.class))
                .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("phone"));
    }

    @Test
    void ac14_theQuerySelectsNoPhoneColumn() {
        assertThat(DonorSearchRepository.SEARCH.toLowerCase(java.util.Locale.ROOT))
                .doesNotContain("phone");
    }

    @Test
    void ac14_theProjectionExposesOnlyTheEightPublishedFields() {
        assertThat(accessorNames(DonorMatchRow.class)).containsExactlyInAnyOrder(
                "getDonorId", "getFullName", "getBloodGroup", "getThanaId",
                "getThanaName", "getThanaDistrict", "getLastDonationDate", "getDistanceKm");
    }

    // ---------- AC-15: nobody nearby is an answer ----------

    @Test
    void ac15_aSearchThatMatchesNobodyIs200WithAnEmptyPage() {
        givenALiveRequestFor(BloodGroup.O_NEGATIVE);
        givenNoMatches();

        PageResponse<DonorMatch> page = service.search(REQUESTER_ID, REQUEST_ID, 10, 0, 20);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    // ---------- the shape of the thing ----------

    @Test
    void theServiceHoldsNoStateOfItsOwn() throws ReflectiveOperationException {
        for (Field field : DonorSearchService.class.getDeclaredFields()) {
            assertThat(field.getType().getName())
                    .as("field %s", field.getName())
                    .isIn(BloodRequestService.class.getName(),
                            DonorSearchRepository.class.getName(),
                            BloodCompatibilityService.class.getName(),
                            EligibilityCalculator.class.getName());
        }
    }

    // ---------- fixtures ----------

    private EligibilityCalculator calculatorAt(Instant instant) {
        return new EligibilityCalculator(Clock.fixed(instant, DHAKA),
                new EligibilityProperties(INTERVAL_DAYS, DHAKA));
    }

    private void givenALiveRequestFor(BloodGroup patientGroup) {
        // Built before the when(), not inside it: stubbing a second mock while a
        // stubbing is still open is an UnfinishedStubbingException.
        BloodRequest request = requestAt(patientGroup,
                new BigDecimal("23.719400"), new BigDecimal("90.396200"));

        when(requests.requireActiveAndOwnedBy(REQUESTER_ID, REQUEST_ID)).thenReturn(request);
    }

    private void givenNoMatches() {
        when(donors.search(anyList(), any(), anyInt(), anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(Page.empty());
    }

    private BloodRequest requestAt(BloodGroup patientGroup, BigDecimal latitude, BigDecimal longitude) {
        Hospital hospital = mock(Hospital.class);
        when(hospital.getLatitude()).thenReturn(latitude);
        when(hospital.getLongitude()).thenReturn(longitude);

        BloodRequest request = mock(BloodRequest.class);
        when(request.getPatientBloodGroup()).thenReturn(patientGroup);
        when(request.getHospital()).thenReturn(hospital);
        return request;
    }

    private DonorMatchRow row(long id, String name, String group, double distanceKm, LocalDate lastDonation) {
        DonorMatchRow row = mock(DonorMatchRow.class);
        when(row.getDonorId()).thenReturn(id);
        when(row.getFullName()).thenReturn(name);
        when(row.getBloodGroup()).thenReturn(group);
        when(row.getThanaId()).thenReturn(7L);
        when(row.getThanaName()).thenReturn("Chawkbazar");
        when(row.getThanaDistrict()).thenReturn("Dhaka");
        when(row.getDistanceKm()).thenReturn(distanceKm);
        when(row.getLastDonationDate()).thenReturn(lastDonation);
        return row;
    }

    private static Stream<String> accessorNames(Class<?> type) {
        return Stream.of(type.getDeclaredMethods()).map(Method::getName);
    }

    private static Stream<String> componentNames(Class<?> record) {
        return Stream.of(record.getRecordComponents()).map(component -> component.getName());
    }

    // ---------- captors ----------

    @SuppressWarnings("unchecked")
    private Collection<String> capturedBloodGroups() {
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(donors).search(captor.capture(), any(), anyInt(), anyDouble(), anyDouble(), anyInt(), any());
        return captor.getValue();
    }

    private LocalDate capturedToday() {
        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
        verify(donors).search(anyList(), captor.capture(), anyInt(), anyDouble(), anyDouble(), anyInt(), any());
        return captor.getValue();
    }

    private int capturedIntervalDays() {
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(donors).search(anyList(), any(), captor.capture(), anyDouble(), anyDouble(), anyInt(), any());
        return captor.getValue();
    }

    private int capturedRadiusKm() {
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(donors).search(anyList(), any(), anyInt(), anyDouble(), anyDouble(), captor.capture(), any());
        return captor.getValue();
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(donors).search(anyList(), any(), anyInt(), anyDouble(), anyDouble(), anyInt(), captor.capture());
        return captor.getValue();
    }
}
