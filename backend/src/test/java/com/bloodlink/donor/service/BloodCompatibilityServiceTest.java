package com.bloodlink.donor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bloodlink.donor.BloodGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * All 64 ordered pairs, twice over.
 *
 * <p>The expected table below is transcribed from SPEC-005 in the same layout, so
 * a reviewer can diff the two by eye. AC-8 then derives the same answers a second
 * time from antigen rules and asserts the two agree — without that, an exhaustive
 * matrix test only proves that two copies of one table match, which is exactly
 * the false comfort it looks like it is protecting against.
 */
class BloodCompatibilityServiceTest {

    /** The eight groups in the order used by the spec's table. */
    private static final BloodGroup[] ORDER = {
        BloodGroup.O_NEGATIVE, BloodGroup.O_POSITIVE,
        BloodGroup.A_NEGATIVE, BloodGroup.A_POSITIVE,
        BloodGroup.B_NEGATIVE, BloodGroup.B_POSITIVE,
        BloodGroup.AB_NEGATIVE, BloodGroup.AB_POSITIVE,
    };

    /**
     * SPEC-005 AC-1, transcribed. One row per patient, columns in {@link #ORDER}.
     *
     * <pre>
     * patient        O-   O+   A-   A+   B-   B+   AB-  AB+
     * </pre>
     */
    private static final String[] EXPECTED_TABLE = {
        /* O-  */ "yes  no   no   no   no   no   no   no",
        /* O+  */ "yes  yes  no   no   no   no   no   no",
        /* A-  */ "yes  no   yes  no   no   no   no   no",
        /* A+  */ "yes  yes  yes  yes  no   no   no   no",
        /* B-  */ "yes  no   no   no   yes  no   no   no",
        /* B+  */ "yes  yes  no   no   yes  yes  no   no",
        /* AB- */ "yes  no   yes  no   yes  no   yes  no",
        /* AB+ */ "yes  yes  yes  yes  yes  yes  yes  yes",
    };

    private final BloodCompatibilityService service = new BloodCompatibilityService();

    // ---------- AC-1: the table, all 64 ordered pairs ----------

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

    @ParameterizedTest(name = "[{index}] patient {0} receives from donor {1} -> {2}")
    @MethodSource("everyOrderedPair")
    void ac1_theTable_holdsForAll64OrderedPairs(BloodGroup patient, BloodGroup donor, boolean expected) {
        assertThat(service.canReceive(patient, donor))
                .as("patient %s receiving from donor %s", patient.getSymbol(), donor.getSymbol())
                .isEqualTo(expected);
    }

    // ---------- AC-2: the count ----------

    @Test
    void ac2_exactly27OfThe64PairsAreCompatible() {
        long compatible = Stream.of(ORDER)
                .flatMap(patient -> Stream.of(ORDER).filter(donor -> service.canReceive(patient, donor)))
                .count();

        assertThat(compatible).isEqualTo(27);
    }

    // ---------- AC-3: the two universal groups ----------

    @ParameterizedTest
    @EnumSource(BloodGroup.class)
    void ac3_oNegativeIsAUniversalDonor(BloodGroup patient) {
        assertThat(service.canReceive(patient, BloodGroup.O_NEGATIVE)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(BloodGroup.class)
    void ac3_abPositiveIsAUniversalRecipient(BloodGroup donor) {
        assertThat(service.canReceive(BloodGroup.AB_POSITIVE, donor)).isTrue();
    }

    // ---------- AC-4: the direction matters ----------

    @Test
    void ac4_theRelationIsNotSymmetric() {
        assertThat(service.canReceive(BloodGroup.A_POSITIVE, BloodGroup.O_POSITIVE)).isTrue();
        assertThat(service.canReceive(BloodGroup.O_POSITIVE, BloodGroup.A_POSITIVE)).isFalse();
    }

    @Test
    void ac4_exactly19UnorderedPairsDisagreeWithTheirReverse() {
        long orderedDisagreements = 0;
        for (BloodGroup patient : ORDER) {
            for (BloodGroup donor : ORDER) {
                if (service.canReceive(patient, donor) != service.canReceive(donor, patient)) {
                    orderedDisagreements++;
                }
            }
        }

        // Each disagreeing pair is counted once in each direction. A transposed
        // table would leave all eight self-pairs correct and this count intact,
        // so AC-1 and this assertion together are what actually pin the
        // orientation.
        assertThat(orderedDisagreements).isEqualTo(38);
        assertThat(orderedDisagreements / 2).isEqualTo(19);
    }

    // ---------- AC-5: the set form agrees, and cannot be mutated ----------

    @ParameterizedTest
    @EnumSource(BloodGroup.class)
    void ac5_compatibleDonorsFor_agreesWithCanReceive(BloodGroup patient) {
        Set<BloodGroup> expected = EnumSet.noneOf(BloodGroup.class);
        for (BloodGroup donor : ORDER) {
            if (service.canReceive(patient, donor)) {
                expected.add(donor);
            }
        }

        assertThat(service.compatibleDonorsFor(patient)).isEqualTo(expected);
        assertThat(service.compatibleDonorsFor(patient)).isNotEmpty();
    }

    @Test
    void ac5_theReturnedSetIsUnmodifiable() {
        Set<BloodGroup> donors = service.compatibleDonorsFor(BloodGroup.O_NEGATIVE);

        assertThatThrownBy(() -> donors.add(BloodGroup.AB_POSITIVE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void ac5_theKnownSetsMatchTheSpec() {
        assertThat(service.compatibleDonorsFor(BloodGroup.B_POSITIVE))
                .containsExactlyInAnyOrder(BloodGroup.O_NEGATIVE, BloodGroup.O_POSITIVE,
                        BloodGroup.B_NEGATIVE, BloodGroup.B_POSITIVE);
        assertThat(service.compatibleDonorsFor(BloodGroup.O_NEGATIVE))
                .containsExactly(BloodGroup.O_NEGATIVE);
        assertThat(service.compatibleDonorsFor(BloodGroup.AB_NEGATIVE))
                .containsExactlyInAnyOrder(BloodGroup.O_NEGATIVE, BloodGroup.A_NEGATIVE,
                        BloodGroup.B_NEGATIVE, BloodGroup.AB_NEGATIVE);
    }

    // ---------- AC-6: no dependencies, and it must stay that way ----------

    @Test
    void ac6_theServiceHasNoDependencies() throws ReflectiveOperationException {
        assertThat(BloodCompatibilityService.class.getDeclaredConstructor()).isNotNull();

        List<String> instanceFields = new ArrayList<>();
        for (Field field : BloodCompatibilityService.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                instanceFields.add(field.getName());
            }
        }

        assertThat(instanceFields)
                .as("a dependency injected here would make the matrix untestable in isolation")
                .isEmpty();
    }

    // ---------- AC-7: null is a caller's bug, not an incompatible match ----------

    @Test
    void ac7_aNullPatientOrDonor_throwsRatherThanReturningFalse() {
        assertThatThrownBy(() -> service.canReceive(null, BloodGroup.O_NEGATIVE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("patient");

        assertThatThrownBy(() -> service.canReceive(BloodGroup.O_NEGATIVE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("donor");

        assertThatThrownBy(() -> service.compatibleDonorsFor(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("patient");
    }

    // ---------- AC-8: a second, independent derivation ----------

    /**
     * Compatibility from first principles: a donor's antigens must all be present
     * in the patient. A donor carrying A needs an A patient, a donor carrying B
     * needs a B patient, and an Rh-positive donor needs an Rh-positive patient.
     *
     * <p>Derived from the group's symbol rather than from the service's table, so
     * this genuinely is a second opinion.
     *
     * @param patient the receiving group
     * @param donor   the giving group
     * @return whether the antigen rule permits the transfusion
     */
    private static boolean permittedByAntigenRule(BloodGroup patient, BloodGroup donor) {
        String patientAntigens = antigenPart(patient);
        String donorAntigens = antigenPart(donor);

        boolean donorNeedsA = donorAntigens.contains("A");
        boolean donorNeedsB = donorAntigens.contains("B");
        boolean donorIsRhPositive = isRhPositive(donor);

        return (!donorNeedsA || patientAntigens.contains("A"))
                && (!donorNeedsB || patientAntigens.contains("B"))
                && (!donorIsRhPositive || isRhPositive(patient));
    }

    private static String antigenPart(BloodGroup group) {
        String symbol = group.getSymbol();
        return symbol.substring(0, symbol.length() - 1);
    }

    private static boolean isRhPositive(BloodGroup group) {
        return group.getSymbol().endsWith("+");
    }

    @ParameterizedTest(name = "[{index}] antigen rule agrees for patient {0}, donor {1}")
    @MethodSource("everyOrderedPair")
    void ac8_anIndependentAntigenDerivation_agreesWithTheTable(BloodGroup patient,
                                                               BloodGroup donor,
                                                               boolean expectedFromTable) {
        boolean fromAntigenRule = permittedByAntigenRule(patient, donor);

        assertThat(fromAntigenRule)
                .as("antigen rule for patient %s, donor %s", patient.getSymbol(), donor.getSymbol())
                .isEqualTo(expectedFromTable);
        assertThat(service.canReceive(patient, donor)).isEqualTo(fromAntigenRule);
    }

    // ---------- evidence for the pull request ----------

    @Test
    void printsTheMatrixForManualVerification() {
        StringBuilder matrix = new StringBuilder();
        matrix.append(System.lineSeparator()).append("patient \\ donor ");
        for (BloodGroup donor : ORDER) {
            matrix.append(String.format("%-5s", donor.getSymbol()));
        }
        matrix.append(System.lineSeparator());

        for (BloodGroup patient : ORDER) {
            matrix.append(String.format("%-16s", patient.getSymbol()));
            for (BloodGroup donor : ORDER) {
                matrix.append(String.format("%-5s", service.canReceive(patient, donor) ? "yes" : "."));
            }
            matrix.append(String.format("  (%d)", service.compatibleDonorsFor(patient).size()));
            matrix.append(System.lineSeparator());
        }

        System.out.println(matrix);
        assertThat(matrix.toString()).contains("yes");
    }
}
