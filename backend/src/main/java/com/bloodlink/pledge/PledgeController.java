package com.bloodlink.pledge;

import com.bloodlink.pledge.service.ContactRevealService;
import com.bloodlink.pledge.service.PledgeService;
import com.bloodlink.request.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pledges, and the one reveal.
 *
 * <p>Mapped at {@code /api} rather than at a single resource root because a
 * pledge is legitimately reachable from three places: under the request it is
 * against, under the donor who made it, and by its own id once it exists. One
 * controller keeps all of them in one file, where the role gates can be read
 * next to each other.
 */
@RestController
@RequestMapping("/api")
@Validated
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Pledges", description = "Offer blood, answer an offer, and the audited contact reveal")
public class PledgeController {

    private final PledgeService pledges;
    private final ContactRevealService reveals;

    public PledgeController(PledgeService pledges, ContactRevealService reveals) {
        this.pledges = pledges;
        this.reveals = reveals;
    }

    @PostMapping("/requests/{requestId}/pledges")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Pledge blood for a request",
            description = "Donors only. The donor must be compatible with the patient and eligible today. "
                    + "An OPEN request becomes PLEDGED. No body: a pledge is 'I will give'.")
    @ApiResponse(responseCode = "201", description = "Pledged, status PENDING")
    @ApiResponse(responseCode = "403", description = "The token is not a DONOR", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such request", content = @Content)
    @ApiResponse(responseCode = "409",
            description = "No profile, incompatible group, not eligible, already pledged, or the request is over",
            content = @Content)
    public PledgeResponse pledge(@AuthenticationPrincipal Jwt token, @PathVariable long requestId) {
        return pledges.pledge(userId(token), requestId);
    }

    @GetMapping("/requests/{requestId}/pledges")
    @Operation(summary = "The pledges on your request",
            description = "The requester who raised it only. No response here carries a phone number.")
    @ApiResponse(responseCode = "200", description = "A page of pledges, newest first")
    @ApiResponse(responseCode = "403", description = "Not your request", content = @Content)
    public PageResponse<PledgeResponse> onRequest(
            @AuthenticationPrincipal Jwt token,
            @PathVariable long requestId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "must be at least 1")
            @Max(value = 100, message = "must be at most 100") int size) {
        return pledges.forRequest(userId(token), requestId, page, size);
    }

    @GetMapping("/donors/me/pledges")
    @Operation(summary = "Your own pledges",
            description = "Every request this donor has offered on, newest first.")
    @ApiResponse(responseCode = "200", description = "A page of pledges")
    public PageResponse<PledgeResponse> mine(
            @AuthenticationPrincipal Jwt token,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "must be at least 1")
            @Max(value = 100, message = "must be at most 100") int size) {
        return pledges.forDonor(userId(token), page, size);
    }

    @PostMapping("/pledges/{id}/accept")
    @Operation(summary = "Accept a pledge",
            description = "The requester picks this donor. The request stays PLEDGED: accepting is not "
                    + "fulfilling. This is what unlocks the contact reveal.")
    @ApiResponse(responseCode = "200", description = "Accepted")
    @ApiResponse(responseCode = "403", description = "Not your request", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such pledge", content = @Content)
    @ApiResponse(responseCode = "409", description = "Not a legal move from the current status",
            content = @Content)
    public PledgeResponse accept(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
        return pledges.accept(userId(token), id);
    }

    @PostMapping("/pledges/{id}/decline")
    @Operation(summary = "Decline a pledge",
            description = "The requester picks somebody else. If no active pledge is left, the request "
                    + "returns to OPEN and reappears in the feed.")
    @ApiResponse(responseCode = "200", description = "Declined")
    @ApiResponse(responseCode = "403", description = "Not your request", content = @Content)
    @ApiResponse(responseCode = "409", description = "Not a legal move from the current status",
            content = @Content)
    public PledgeResponse decline(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
        return pledges.decline(userId(token), id);
    }

    @PostMapping("/pledges/{id}/withdraw")
    @Operation(summary = "Withdraw your pledge",
            description = "The donor pulls out. Legal from ACCEPTED as well as PENDING: a donor who "
                    + "cannot come must be able to say so.")
    @ApiResponse(responseCode = "200", description = "Withdrawn")
    @ApiResponse(responseCode = "403", description = "Not your pledge", content = @Content)
    @ApiResponse(responseCode = "409", description = "Not a legal move from the current status",
            content = @Content)
    public PledgeResponse withdraw(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
        return pledges.withdraw(userId(token), id);
    }

    @GetMapping("/pledges/{id}/contact")
    @Operation(summary = "The contact reveal",
            description = "The ONLY endpoint in BloodLink that returns a phone number. Available to the "
                    + "two parties of an ACCEPTED pledge and to nobody else, and every call appends a "
                    + "row to the audit log.")
    @ApiResponse(responseCode = "200", description = "The other party, with their number")
    @ApiResponse(responseCode = "403", description = "Not a party to this pledge", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such pledge", content = @Content)
    @ApiResponse(responseCode = "409", description = "The pledge has not been accepted", content = @Content)
    public ContactResponse contact(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
        return reveals.reveal(userId(token), id);
    }

    private static long userId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }
}
