package com.bloodlink.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bloodlink.request.BloodRequestStatus;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * All 25 ordered pairs of statuses.
 *
 * <p>The value of a request having a lifecycle is entirely in the moves it
 * refuses, so the refusals are what get asserted — every one of the eighteen,
 * not a sample.
 */
class BloodRequestStateMachineTest {

    private static final BloodRequestStatus[] ORDER = {
        BloodRequestStatus.OPEN,
        BloodRequestStatus.PLEDGED,
        BloodRequestStatus.FULFILLED,
        BloodRequestStatus.CANCELLED,
        BloodRequestStatus.EXPIRED,
    };

    /**
     * SPEC-006 AC-10, transcribed. One row per current status, columns in
     * {@link #ORDER}.
     *
     * <pre>
     * from            OPEN  PLEDGED  FULFILLED  CANCELLED  EXPIRED
     * </pre>
     */
    private static final String[] EXPECTED_TABLE = {
        /* OPEN      */ "no   yes  no   yes  yes",
        /* PLEDGED   */ "yes  no   yes  yes  yes",
        /* FULFILLED */ "no   no   no   no   no",
        /* CANCELLED */ "no   no   no   no   no",
        /* EXPIRED   */ "no   no   no   no   no",
    };

    private final BloodRequestStateMachine stateMachine = new BloodRequestStateMachine();

    static Stream<Arguments> everyOrderedPair() {
        List<Arguments> pairs = new ArrayList<>();
        for (int row = 0; row < ORDER.length; row++) {
            String[] cells = EXPECTED_TABLE[row].trim().split("\\s+");
            for (int column = 0; column < ORDER.length; column++) {
                pairs.add(Arguments.of(ORDER[row], ORDER[column], "yes".equals(cells[column])));
            }
        }
        return pairs.stream();
    }

    // ---------- AC-10: the table ----------

    @ParameterizedTest(name = "[{index}] {0} -> {1} is {2}")
    @MethodSource("everyOrderedPair")
    void ac10_theTransitionTable_holdsForAll25OrderedPairs(BloodRequestStatus from,
                                                           BloodRequestStatus to,
                                                           boolean expected) {
        assertThat(stateMachine.canTransition(from, to))
                .as("%s -> %s", from, to)
                .isEqualTo(expected);
    }

    @Test
    void ac10_exactly7OfThe25PairsAreLegal() {
        long legal = Stream.of(ORDER)
                .flatMap(from -> Stream.of(ORDER).filter(to -> stateMachine.canTransition(from, to)))
                .count();

        assertThat(legal).isEqualTo(7);
    }

    // ---------- AC-11: nothing moves to itself ----------

    @ParameterizedTest
    @EnumSource(BloodRequestStatus.class)
    void ac11_noStatusTransitionsToItself(BloodRequestStatus status) {
        assertThat(stateMachine.canTransition(status, status))
                .as("a repeated request to do something already done is a conflict, not a no-op")
                .isFalse();
    }

    // ---------- AC-9: terminal means terminal ----------

    @ParameterizedTest
    @EnumSource(value = BloodRequestStatus.class, names = {"FULFILLED", "CANCELLED", "EXPIRED"})
    void ac9_aTerminalStatusHasNoLegalMoveOut(BloodRequestStatus terminal) {
        assertThat(stateMachine.allowedFrom(terminal)).isEmpty();
        assertThat(terminal.isTerminal()).isTrue();

        for (BloodRequestStatus target : ORDER) {
            assertThatThrownBy(() -> stateMachine.requireTransition(terminal, target))
                    .isInstanceOf(IllegalTransitionException.class);
        }
    }

    @ParameterizedTest
    @EnumSource(value = BloodRequestStatus.class, names = {"OPEN", "PLEDGED"})
    void ac9_anActiveStatusIsNotTerminal(BloodRequestStatus active) {
        assertThat(active.isTerminal()).isFalse();
        assertThat(stateMachine.allowedFrom(active)).isNotEmpty();
    }

    // ---------- AC-8: the headline guard ----------

    @Test
    void ac8_anOpenRequestCannotBeFulfilled() {
        assertThat(stateMachine.canTransition(BloodRequestStatus.OPEN, BloodRequestStatus.FULFILLED))
                .as("nobody has offered blood yet, so there is nothing to confirm")
                .isFalse();

        assertThatThrownBy(() -> stateMachine.requireTransition(
                BloodRequestStatus.OPEN, BloodRequestStatus.FULFILLED))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessage("A request cannot move from OPEN to FULFILLED");
    }

    @Test
    void ac8_aPledgedRequestCanBeFulfilled() {
        assertThatCode(() -> stateMachine.requireTransition(
                BloodRequestStatus.PLEDGED, BloodRequestStatus.FULFILLED))
                .doesNotThrowAnyException();
    }

    // ---------- AC-12: a withdrawn pledge returns the request to the feed ----------

    @Test
    void ac12_aPledgedRequestCanReturnToOpen() {
        assertThat(stateMachine.canTransition(BloodRequestStatus.PLEDGED, BloodRequestStatus.OPEN))
                .as("a request whose only pledge is withdrawn must not be stranded")
                .isTrue();

        assertThat(stateMachine.canTransition(BloodRequestStatus.OPEN, BloodRequestStatus.PLEDGED))
                .isTrue();
    }

    // ---------- the shape of the thing ----------

    @Test
    void ac10_allowedFromAgreesWithCanTransition() {
        for (BloodRequestStatus from : ORDER) {
            for (BloodRequestStatus to : ORDER) {
                assertThat(stateMachine.allowedFrom(from).contains(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(stateMachine.canTransition(from, to));
            }
        }
    }

    @Test
    void ac10_theReturnedSetIsUnmodifiable() {
        assertThatThrownBy(() -> stateMachine.allowedFrom(BloodRequestStatus.OPEN)
                .add(BloodRequestStatus.FULFILLED))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void ac10_theStateMachineHasNoDependencies() throws ReflectiveOperationException {
        assertThat(BloodRequestStateMachine.class.getDeclaredConstructor()).isNotNull();

        List<String> instanceFields = new ArrayList<>();
        for (Field field : BloodRequestStateMachine.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                instanceFields.add(field.getName());
            }
        }

        assertThat(instanceFields)
                .as("a table of legal moves is only cheap to test exhaustively while it needs nothing")
                .isEmpty();
    }

    @Test
    void ac10_nullStatusesAreRejected() {
        assertThatThrownBy(() -> stateMachine.canTransition(null, BloodRequestStatus.OPEN))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("current");
        assertThatThrownBy(() -> stateMachine.canTransition(BloodRequestStatus.OPEN, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("target");
        assertThatThrownBy(() -> stateMachine.allowedFrom(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---------- evidence for the pull request ----------

    @Test
    void printsTheTransitionTableForManualVerification() {
        StringBuilder table = new StringBuilder(System.lineSeparator());
        table.append(String.format("%-12s", "from \\ to"));
        for (BloodRequestStatus to : ORDER) {
            table.append(String.format("%-11s", to));
        }
        table.append(System.lineSeparator());

        for (BloodRequestStatus from : ORDER) {
            table.append(String.format("%-12s", from));
            for (BloodRequestStatus to : ORDER) {
                table.append(String.format("%-11s", stateMachine.canTransition(from, to) ? "yes" : "."));
            }
            table.append(System.lineSeparator());
        }

        System.out.println(table);
        assertThat(table.toString()).contains("yes");
    }
}
