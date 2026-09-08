package com.bloodlink.pledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bloodlink.pledge.PledgeStatus;
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
 * All 16 ordered pairs of pledge statuses.
 *
 * <p>Same discipline as {@code BloodRequestStateMachineTest}: the value of a
 * pledge having a lifecycle is in the twelve moves it refuses, so every refusal
 * is asserted rather than a sample.
 */
class PledgeStateMachineTest {

    private static final PledgeStatus[] ORDER = {
        PledgeStatus.PENDING,
        PledgeStatus.ACCEPTED,
        PledgeStatus.DECLINED,
        PledgeStatus.WITHDRAWN,
    };

    /**
     * SPEC-008 AC-20, transcribed. One row per current status, columns in
     * {@link #ORDER}.
     *
     * <pre>
     * from          PENDING  ACCEPTED  DECLINED  WITHDRAWN
     * </pre>
     */
    private static final String[] EXPECTED_TABLE = {
        /* PENDING   */ "no   yes  yes  yes",
        /* ACCEPTED  */ "no   no   no   yes",
        /* DECLINED  */ "no   no   no   no",
        /* WITHDRAWN */ "no   no   no   no",
    };

    private final PledgeStateMachine stateMachine = new PledgeStateMachine();

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

    @ParameterizedTest(name = "[{index}] {0} -> {1} is {2}")
    @MethodSource("everyOrderedPair")
    void ac20_theTransitionTable_holdsForAll16OrderedPairs(PledgeStatus from,
                                                           PledgeStatus to,
                                                           boolean expected) {
        assertThat(stateMachine.canTransition(from, to))
                .as("%s -> %s", from, to)
                .isEqualTo(expected);
    }

    @Test
    void ac20_exactly4OfThe16PairsAreLegal() {
        long legal = Stream.of(ORDER)
                .flatMap(from -> Stream.of(ORDER).filter(to -> stateMachine.canTransition(from, to)))
                .count();

        assertThat(legal).isEqualTo(4);
    }

    @ParameterizedTest
    @EnumSource(PledgeStatus.class)
    void ac20_noStatusTransitionsToItself(PledgeStatus status) {
        assertThat(stateMachine.canTransition(status, status))
                .as("accepting an accepted pledge means the requester believed something untrue")
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = PledgeStatus.class, names = {"DECLINED", "WITHDRAWN"})
    void ac11_aTerminalStatusHasNoLegalMoveOut(PledgeStatus terminal) {
        assertThat(stateMachine.allowedFrom(terminal)).isEmpty();
        assertThat(terminal.isTerminal()).isTrue();
        assertThat(terminal.isActive()).isFalse();

        for (PledgeStatus target : ORDER) {
            assertThatThrownBy(() -> stateMachine.requireTransition(terminal, target))
                    .isInstanceOf(IllegalPledgeTransitionException.class);
        }
    }

    @ParameterizedTest
    @EnumSource(value = PledgeStatus.class, names = {"PENDING", "ACCEPTED"})
    void ac14_pendingAndAcceptedAreTheActiveStatuses(PledgeStatus active) {
        assertThat(active.isActive())
                .as("active is what keeps a request in PLEDGED")
                .isTrue();
        assertThat(active.isTerminal()).isFalse();
    }

    @Test
    void ac11_anAlreadyAcceptedPledgeCannotBeAcceptedAgain() {
        assertThatThrownBy(() -> stateMachine.requireTransition(
                PledgeStatus.ACCEPTED, PledgeStatus.ACCEPTED))
                .isInstanceOf(IllegalPledgeTransitionException.class)
                .hasMessage("A pledge cannot move from ACCEPTED to ACCEPTED");
    }

    @Test
    void ac11_aDeclinedPledgeCannotBeAccepted() {
        assertThatThrownBy(() -> stateMachine.requireTransition(
                PledgeStatus.DECLINED, PledgeStatus.ACCEPTED))
                .isInstanceOf(IllegalPledgeTransitionException.class)
                .hasMessage("A pledge cannot move from DECLINED to ACCEPTED");
    }

    @Test
    void ac20_anAcceptedPledgeMayStillBeWithdrawn() {
        assertThatCode(() -> stateMachine.requireTransition(
                PledgeStatus.ACCEPTED, PledgeStatus.WITHDRAWN))
                .as("a donor who cannot come must be able to say so")
                .doesNotThrowAnyException();
    }

    @Test
    void ac20_anAcceptedPledgeCannotBeDeclined() {
        assertThat(stateMachine.canTransition(PledgeStatus.ACCEPTED, PledgeStatus.DECLINED))
                .as("a requester who changes their mind after accepting is not covered by this spec")
                .isFalse();
    }

    // ---------- the shape of the thing ----------

    @Test
    void ac20_allowedFromAgreesWithCanTransition() {
        for (PledgeStatus from : ORDER) {
            for (PledgeStatus to : ORDER) {
                assertThat(stateMachine.allowedFrom(from).contains(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(stateMachine.canTransition(from, to));
            }
        }
    }

    @Test
    void ac20_theReturnedSetIsUnmodifiable() {
        assertThatThrownBy(() -> stateMachine.allowedFrom(PledgeStatus.PENDING)
                .add(PledgeStatus.PENDING))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void ac20_theStateMachineHasNoDependencies() throws ReflectiveOperationException {
        assertThat(PledgeStateMachine.class.getDeclaredConstructor()).isNotNull();

        List<String> instanceFields = new ArrayList<>();
        for (Field field : PledgeStateMachine.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                instanceFields.add(field.getName());
            }
        }

        assertThat(instanceFields).isEmpty();
    }

    @Test
    void ac20_nullStatusesAreRejected() {
        assertThatThrownBy(() -> stateMachine.canTransition(null, PledgeStatus.ACCEPTED))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("current");
        assertThatThrownBy(() -> stateMachine.canTransition(PledgeStatus.PENDING, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("target");
        assertThatThrownBy(() -> stateMachine.allowedFrom(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void printsTheTransitionTableForManualVerification() {
        StringBuilder table = new StringBuilder(System.lineSeparator());
        table.append(String.format("%-12s", "from \\ to"));
        for (PledgeStatus to : ORDER) {
            table.append(String.format("%-11s", to));
        }
        table.append(System.lineSeparator());

        for (PledgeStatus from : ORDER) {
            table.append(String.format("%-12s", from));
            for (PledgeStatus to : ORDER) {
                table.append(String.format("%-11s", stateMachine.canTransition(from, to) ? "yes" : "."));
            }
            table.append(System.lineSeparator());
        }

        System.out.println(table);
        assertThat(table.toString()).contains("yes");
    }
}
