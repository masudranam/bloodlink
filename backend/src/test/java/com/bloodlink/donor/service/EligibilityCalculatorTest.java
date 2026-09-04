package com.bloodlink.donor.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The eligibility boundary, pinned day by day.
 *
 * <p>This is the arithmetic pillar 2 rests on, and it is the easiest thing in the
 * project to get quietly wrong by one day, so the clock is fixed and every
 * interesting offset is asserted rather than sampled.
 */
class EligibilityCalculatorTest {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final int NINETY_DAYS = 90;

    /** Midday in Dhaka on 2026-09-03, so nothing here depends on when it runs. */
    private static final Instant NOON_IN_DHAKA = Instant.parse("2026-09-03T06:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);

    private EligibilityCalculator calculator(int intervalDays, ZoneId zone, Instant now) {
        return new EligibilityCalculator(
                Clock.fixed(now, zone),
                new EligibilityProperties(intervalDays, zone));
    }

    private EligibilityCalculator calculator() {
        return calculator(NINETY_DAYS, DHAKA, NOON_IN_DHAKA);
    }

    // ---------- AC-7: the table from the spec, verbatim ----------

    @ParameterizedTest(name = "lastDonation today{0} -> eligible={1}, next=today{2}")
    @CsvSource({
        "-92, true,  -1",
        "-91, true,   0",
        "-90, false,  1",
        "-89, false,  2",
        "  0, false, 91",
    })
    void ac7_eligibilityAndNextDate_followTheIntervalExactly(long offsetDays,
                                                             boolean expectedEligible,
                                                             long expectedNextOffset) {
        EligibilityCalculator calculator = calculator();
        LocalDate lastDonation = TODAY.plusDays(offsetDays);

        assertThat(calculator.isEligible(lastDonation)).isEqualTo(expectedEligible);
        assertThat(calculator.nextEligibleDate(lastDonation)).isEqualTo(TODAY.plusDays(expectedNextOffset));
    }

    @Test
    void ac7_theBoundaryDay_isTheFirstEligibleDay() {
        EligibilityCalculator calculator = calculator();

        // Day 90 after donating is one day too early; day 91 is allowed. This is
        // the strict inequality in "lastDonationDate + interval < today".
        assertThat(calculator.isEligible(TODAY.minusDays(90))).isFalse();
        assertThat(calculator.isEligible(TODAY.minusDays(91))).isTrue();
        assertThat(calculator.nextEligibleDate(TODAY.minusDays(91))).isEqualTo(TODAY);
    }

    @Test
    void ac7_theIntervalIsEchoedFromConfiguration() {
        assertThat(calculator().donationIntervalDays()).isEqualTo(90);
        assertThat(calculator(120, DHAKA, NOON_IN_DHAKA).donationIntervalDays()).isEqualTo(120);
    }

    // ---------- AC-8: never donated ----------

    @Test
    void ac8_aDonorWhoHasNeverDonated_isEligibleWithNoNextDate() {
        EligibilityCalculator calculator = calculator();

        assertThat(calculator.isEligible(null)).isTrue();
        assertThat(calculator.nextEligibleDate(null)).isNull();
    }

    // ---------- AC-9: the answer follows configuration, because nothing is stored ----------

    @Test
    void ac9_changingTheInterval_changesTheAnswerForTheSameDate() {
        LocalDate hundredDaysAgo = TODAY.minusDays(100);

        EligibilityCalculator ninety = calculator(90, DHAKA, NOON_IN_DHAKA);
        EligibilityCalculator hundredAndTwenty = calculator(120, DHAKA, NOON_IN_DHAKA);

        assertThat(ninety.isEligible(hundredDaysAgo)).isTrue();
        assertThat(hundredAndTwenty.isEligible(hundredDaysAgo)).isFalse();

        assertThat(ninety.nextEligibleDate(hundredDaysAgo)).isEqualTo(hundredDaysAgo.plusDays(91));
        assertThat(hundredAndTwenty.nextEligibleDate(hundredDaysAgo)).isEqualTo(hundredDaysAgo.plusDays(121));
    }

    // ---------- AC-13: today is the donor's day, not the server's ----------

    @Test
    void ac13_todayIsEvaluatedInDhakaNotUtc() {
        // 2026-09-03T20:00Z is still the 3rd in London and already the 4th in
        // Dhaka. A donor refreshing after 6pm UTC must not be told it is
        // yesterday.
        Instant lateEveningUtc = Instant.parse("2026-09-03T20:00:00Z");

        assertThat(calculator(NINETY_DAYS, DHAKA, lateEveningUtc).today())
                .isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(calculator(NINETY_DAYS, ZoneOffset.UTC, lateEveningUtc).today())
                .isEqualTo(LocalDate.of(2026, 9, 3));
    }

    @Test
    void ac13_theDayBoundaryShiftsEligibilityWithTheZone() {
        Instant lateEveningUtc = Instant.parse("2026-09-03T20:00:00Z");
        // Donated 91 days before 2026-09-04, so eligible in Dhaka where it is
        // already the 4th, and not yet eligible in UTC where it is still the 3rd.
        LocalDate lastDonation = LocalDate.of(2026, 9, 4).minusDays(91);

        assertThat(calculator(NINETY_DAYS, DHAKA, lateEveningUtc).isEligible(lastDonation)).isTrue();
        assertThat(calculator(NINETY_DAYS, ZoneOffset.UTC, lateEveningUtc).isEligible(lastDonation)).isFalse();
    }
}
