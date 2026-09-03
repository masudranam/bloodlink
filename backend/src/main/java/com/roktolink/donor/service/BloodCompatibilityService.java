package com.roktolink.donor.service;

import static com.roktolink.donor.BloodGroup.AB_NEGATIVE;
import static com.roktolink.donor.BloodGroup.AB_POSITIVE;
import static com.roktolink.donor.BloodGroup.A_NEGATIVE;
import static com.roktolink.donor.BloodGroup.A_POSITIVE;
import static com.roktolink.donor.BloodGroup.B_NEGATIVE;
import static com.roktolink.donor.BloodGroup.B_POSITIVE;
import static com.roktolink.donor.BloodGroup.O_NEGATIVE;
import static com.roktolink.donor.BloodGroup.O_POSITIVE;

import com.roktolink.donor.BloodGroup;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Whether one blood group may receive red cells from another.
 *
 * <p>This is the only piece of RoktoLink where being wrong is dangerous rather
 * than merely annoying, so it is also the only piece with no dependencies at all:
 * no repository, no clock, no user, no state. Given two blood groups it returns
 * an answer, which is what lets all 64 combinations be asserted exhaustively.
 *
 * <p>The table below is written out one line per patient group rather than
 * derived from antigen arithmetic, even though the arithmetic would be shorter.
 * Someone with medical knowledge and no Java should be able to read it against a
 * reference chart. The antigen derivation lives in the test, where two
 * independent statements of the same rule are made to agree.
 *
 * <p><strong>Red cells only.</strong> Plasma compatibility runs the other way
 * round — an AB patient is a universal plasma donor. Adding a parameter here to
 * cover it would put two opposite tables behind one method name; it gets its own
 * service if it is ever needed.
 */
@Service
public class BloodCompatibilityService {

    /**
     * Patient group to the groups they may receive from.
     *
     * <p>Read as: a patient in the key's group can accept red cells from any donor
     * in the value's set.
     */
    private static final Map<BloodGroup, Set<BloodGroup>> COMPATIBLE_DONORS;

    static {
        Map<BloodGroup, Set<BloodGroup>> table = new EnumMap<>(BloodGroup.class);

        table.put(O_NEGATIVE, EnumSet.of(O_NEGATIVE));
        table.put(O_POSITIVE, EnumSet.of(O_NEGATIVE, O_POSITIVE));
        table.put(A_NEGATIVE, EnumSet.of(O_NEGATIVE, A_NEGATIVE));
        table.put(A_POSITIVE, EnumSet.of(O_NEGATIVE, O_POSITIVE, A_NEGATIVE, A_POSITIVE));
        table.put(B_NEGATIVE, EnumSet.of(O_NEGATIVE, B_NEGATIVE));
        table.put(B_POSITIVE, EnumSet.of(O_NEGATIVE, O_POSITIVE, B_NEGATIVE, B_POSITIVE));
        table.put(AB_NEGATIVE, EnumSet.of(O_NEGATIVE, A_NEGATIVE, B_NEGATIVE, AB_NEGATIVE));
        table.put(AB_POSITIVE, EnumSet.allOf(BloodGroup.class));

        Map<BloodGroup, Set<BloodGroup>> unmodifiable = new EnumMap<>(BloodGroup.class);
        table.forEach((patient, donors) -> unmodifiable.put(patient, Collections.unmodifiableSet(donors)));
        COMPATIBLE_DONORS = Collections.unmodifiableMap(unmodifiable);
    }

    /**
     * Whether a patient may receive red cells from a donor.
     *
     * <p>The order of the arguments is the whole point: this asks "can the patient
     * receive from the donor", which is not the same question as the reverse. A+
     * may receive from O+, and O+ may not receive from A+.
     *
     * @param patient the group of the person receiving blood
     * @param donor   the group of the person giving blood
     * @return true when the transfusion is allowed
     * @throws NullPointerException if either group is null — returning false would
     *                              let a caller's bug look like an incompatible match
     */
    public boolean canReceive(BloodGroup patient, BloodGroup donor) {
        Objects.requireNonNull(patient, "patient blood group must not be null");
        Objects.requireNonNull(donor, "donor blood group must not be null");
        return COMPATIBLE_DONORS.get(patient).contains(donor);
    }

    /**
     * Every group a patient may receive from.
     *
     * <p>This is the form donor search needs: the set goes into a native
     * {@code IN (...)} clause so the database does the filtering, rather than
     * fetching every donor and calling {@link #canReceive} in a loop.
     *
     * @param patient the group of the person receiving blood
     * @return an unmodifiable set of acceptable donor groups, never empty — every
     *         group can at least receive from O-
     * @throws NullPointerException if the group is null
     */
    public Set<BloodGroup> compatibleDonorsFor(BloodGroup patient) {
        Objects.requireNonNull(patient, "patient blood group must not be null");
        return COMPATIBLE_DONORS.get(patient);
    }
}
