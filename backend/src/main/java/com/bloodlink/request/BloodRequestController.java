package com.bloodlink.request;

import com.bloodlink.request.service.BloodRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Blood requests: raising them, reading them, and the two moves a requester
 * drives themselves.
 *
 * <p>There is deliberately no endpoint that sets an arbitrary status. Reaching
 * PLEDGED is a donor pledging (SPEC-008) and reaching EXPIRED is time passing
 * (SPEC-010); neither is something a client asks for.
 */
@RestController
@RequestMapping("/api/requests")
@Validated
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Blood requests", description = "Raise a request, browse the feed, cancel or fulfil your own")
public class BloodRequestController {

    private final BloodRequestService bloodRequests;

    public BloodRequestController(BloodRequestService bloodRequests) {
        this.bloodRequests = bloodRequests;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Raise a blood request",
            description = "Always created OPEN. Requesters only.")
    @ApiResponse(responseCode = "201", description = "Request raised")
    @ApiResponse(responseCode = "400", description = "Invalid body, unknown hospital, or a past date",
            content = @Content)
    @ApiResponse(responseCode = "403", description = "The token is not a REQUESTER", content = @Content)
    public BloodRequestResponse create(@AuthenticationPrincipal Jwt token,
                                       @Valid @RequestBody CreateBloodRequest body) {
        return bloodRequests.create(userId(token), body);
    }

    @GetMapping
    @Operation(summary = "The request feed",
            description = "Open requests by default, newest first. No response here carries a phone number.")
    @ApiResponse(responseCode = "200", description = "A page of requests")
    public PageResponse<BloodRequestResponse> feed(
            @RequestParam(defaultValue = "OPEN") BloodRequestStatus status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "must be at least 1")
            @Max(value = 100, message = "must be at most 100") int size) {
        return bloodRequests.feed(status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One request")
    @ApiResponse(responseCode = "200", description = "The request")
    @ApiResponse(responseCode = "404", description = "No such request", content = @Content)
    public BloodRequestResponse get(@PathVariable long id) {
        return bloodRequests.get(id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a request you raised",
            description = "Legal from OPEN and PLEDGED. Terminal statuses refuse it.")
    @ApiResponse(responseCode = "200", description = "Cancelled")
    @ApiResponse(responseCode = "403", description = "Not your request", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such request", content = @Content)
    @ApiResponse(responseCode = "409", description = "Not a legal move from the current status",
            content = @Content)
    public BloodRequestResponse cancel(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
        return bloodRequests.cancel(userId(token), id);
    }

    @PostMapping("/{id}/fulfil")
    @Operation(summary = "Confirm blood was given",
            description = "Legal only from PLEDGED: with nobody having offered there is nothing to confirm.")
    @ApiResponse(responseCode = "200", description = "Fulfilled")
    @ApiResponse(responseCode = "403", description = "Not your request", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such request", content = @Content)
    @ApiResponse(responseCode = "409", description = "The request is not PLEDGED", content = @Content)
    public BloodRequestResponse fulfil(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
        return bloodRequests.fulfil(userId(token), id);
    }

    private static long userId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }
}
