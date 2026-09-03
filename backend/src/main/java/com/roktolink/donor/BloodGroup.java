package com.roktolink.donor;

/**
 * The eight blood groups.
 *
 * <p>The constants cannot be named {@code A+} because {@code +} is not legal in a
 * Java identifier, while the database stores the readable symbol so that the
 * native SQL SPEC-007 writes by hand stays legible. {@link BloodGroupConverter}
 * bridges the two.
 */
public enum BloodGroup {

    A_POSITIVE("A+"),
    A_NEGATIVE("A-"),
    B_POSITIVE("B+"),
    B_NEGATIVE("B-"),
    AB_POSITIVE("AB+"),
    AB_NEGATIVE("AB-"),
    O_POSITIVE("O+"),
    O_NEGATIVE("O-");

    private final String symbol;

    BloodGroup(String symbol) {
        this.symbol = symbol;
    }

    /**
     * The canonical symbol as stored in the database, for example {@code AB-}.
     *
     * @return the three-character-or-less symbol
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Resolves a stored symbol back to a constant.
     *
     * @param symbol the canonical symbol, for example {@code O+}
     * @return the matching constant
     * @throws IllegalArgumentException if no group uses that symbol
     */
    public static BloodGroup fromSymbol(String symbol) {
        for (BloodGroup group : values()) {
            if (group.symbol.equals(symbol)) {
                return group;
            }
        }
        throw new IllegalArgumentException("Not a blood group symbol: " + symbol);
    }
}
