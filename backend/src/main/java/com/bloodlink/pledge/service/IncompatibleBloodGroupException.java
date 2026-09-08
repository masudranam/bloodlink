package com.bloodlink.pledge.service;

import com.bloodlink.donor.BloodGroup;

/**
 * Thrown when a donor's blood group is not one the patient can receive.
 *
 * The answer comes from BloodCompatibilityService, the same table donor search
 * filters on. Nothing here decides compatibility for itself.
 */
public class IncompatibleBloodGroupException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IncompatibleBloodGroupException(BloodGroup patient, BloodGroup donor) {
        super("A " + patient.getSymbol() + " patient cannot receive from a "
                + donor.getSymbol() + " donor");
    }
}
