package com.bloodlink.reference;

import com.bloodlink.donor.ThanaSummary;
import com.bloodlink.request.HospitalSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The seeded reference data, so a client can offer a choice instead of asking
 * somebody to type an id.
 *
 * <p>Added during SPEC-009 rather than in the spec that seeded the data. Two
 * merged specs take a {@code thanaId} (SPEC-004) and a {@code hospitalId}
 * (SPEC-006), and nothing exposed either list, so the only client a person could
 * use would have had a number box labelled "hospital id". This fills that gap and
 * nothing more: two read-only lists over data that has been in {@code V2} since
 * SPEC-002.
 *
 * <p>Both are unpaged. There are 40 thanas and 4 hospitals, they change when a
 * migration changes them, and a page control over a fixed list of 40 rows would
 * be ceremony.
 *
 * <p>Neither response can carry a phone number: these are places, not people.
 * Coordinates are also absent — they are inputs to the distance ranking in
 * SPEC-007 and a client has no use for them.
 */
@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Reference data", description = "Thanas and hospitals, for populating a form")
public class ReferenceController {

    private final ThanaRepository thanas;
    private final HospitalRepository hospitals;

    public ReferenceController(ThanaRepository thanas, HospitalRepository hospitals) {
        this.thanas = thanas;
        this.hospitals = hospitals;
    }

    /**
     * Every thana a donor may choose, by name.
     *
     * @return all thanas, alphabetically
     */
    @GetMapping("/thanas")
    @Operation(summary = "Every thana", description = "Alphabetical. A donor's location is one of these.")
    @ApiResponse(responseCode = "200", description = "The full list, unpaged")
    public List<ThanaSummary> thanas() {
        return thanas.findAll(Sort.by("name")).stream()
                .map(thana -> new ThanaSummary(thana.getId(), thana.getName(), thana.getDistrict()))
                .toList();
    }

    /**
     * Every hospital a request may be raised against.
     *
     * @return all hospitals, alphabetically
     */
    @GetMapping("/hospitals")
    @Operation(summary = "Every hospital",
            description = "Alphabetical. Distance in donor search is measured from one of these.")
    @ApiResponse(responseCode = "200", description = "The full list, unpaged")
    public List<HospitalSummary> hospitals() {
        return hospitals.findAllWithThana().stream()
                .map(hospital -> new HospitalSummary(
                        hospital.getId(), hospital.getName(), hospital.getThana().getName()))
                .toList();
    }
}
