package com.bloodlink.donor;

import com.bloodlink.donor.service.DonorProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A donor's own profile.
 *
 * <p>Every endpoint here acts on the caller and only the caller: the user id
 * comes from the token's subject, never from the path or the body, so there is no
 * way to address someone else's profile. Reading other donors is SPEC-007, and it
 * will return a projection rather than this shape.
 *
 * <p>The whole path is restricted to {@code ROLE_DONOR} in the filter chain.
 */
@RestController
@RequestMapping("/api/donors/me")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Donor profile", description = "The caller's own donor profile, with computed eligibility")
public class DonorProfileController {

    private final DonorProfileService donorProfiles;

    public DonorProfileController(DonorProfileService donorProfiles) {
        this.donorProfiles = donorProfiles;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create the caller's donor profile",
            description = "isEligible and nextEligibleDate are computed, not stored.")
    @ApiResponse(responseCode = "201", description = "Profile created")
    @ApiResponse(responseCode = "400", description = "Invalid body, unknown thana, or a future date",
            content = @Content)
    @ApiResponse(responseCode = "403", description = "The token is not a DONOR", content = @Content)
    @ApiResponse(responseCode = "409", description = "This donor already has a profile", content = @Content)
    public DonorProfileResponse create(@AuthenticationPrincipal Jwt token,
                                       @Valid @RequestBody DonorProfileRequest request) {
        return donorProfiles.create(userId(token), request);
    }

    @GetMapping
    @Operation(summary = "Read the caller's donor profile")
    @ApiResponse(responseCode = "200", description = "The profile, with eligibility computed now")
    @ApiResponse(responseCode = "404", description = "No profile yet", content = @Content)
    public DonorProfileResponse get(@AuthenticationPrincipal Jwt token) {
        return donorProfiles.get(userId(token));
    }

    @PutMapping
    @Operation(summary = "Replace the caller's donor profile",
            description = "A full replacement: an omitted lastDonationDate clears it.")
    @ApiResponse(responseCode = "200", description = "The updated profile")
    @ApiResponse(responseCode = "400", description = "Invalid body, unknown thana, or a future date",
            content = @Content)
    @ApiResponse(responseCode = "404", description = "No profile to replace", content = @Content)
    public DonorProfileResponse replace(@AuthenticationPrincipal Jwt token,
                                        @Valid @RequestBody DonorProfileRequest request) {
        return donorProfiles.replace(userId(token), request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete the caller's donor profile",
            description = "Removes the offer to donate. The account itself survives.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No profile to delete", content = @Content)
    public void delete(@AuthenticationPrincipal Jwt token) {
        donorProfiles.delete(userId(token));
    }

    private static long userId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }
}
