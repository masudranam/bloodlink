package com.bloodlink.pledge;

import com.bloodlink.pledge.service.ContactRevealService;
import com.bloodlink.request.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who has seen your number.
 *
 * <p>Separate from {@link PledgeController} because it is a different question
 * asked by a different person. The pledge endpoints are about arranging a
 * donation; this one exists so that the promise the project makes about phone
 * numbers is checkable by the people it is made to.
 *
 * <p>Open to both roles: a requester's number is revealed to a donor exactly as
 * a donor's is to a requester, so both can audit it.
 */
@RestController
@RequestMapping("/api/me/reveals")
@Validated
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Contact reveal log", description = "Who has seen your phone number, and when")
public class RevealLogController {

    private final ContactRevealService reveals;

    public RevealLogController(ContactRevealService reveals) {
        this.reveals = reveals;
    }

    /**
     * Every reveal of the caller's own number, newest first.
     *
     * @param token the caller's token
     * @param page  zero-based page number
     * @param size  page size, 1 to 100
     * @return a page of reveals, carrying no phone number at all
     */
    @GetMapping
    @Operation(summary = "Reveals of your own phone number",
            description = "Newest first. Names who looked, in what role, against which request, and "
                    + "when. Carries no phone number - not the viewer's, and not your own.")
    @ApiResponse(responseCode = "200", description = "A page of reveals, possibly empty")
    public PageResponse<RevealResponse> mine(
            @AuthenticationPrincipal Jwt token,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "must be at least 1")
            @Max(value = 100, message = "must be at most 100") int size) {
        return reveals.myReveals(Long.parseLong(token.getSubject()), page, size);
    }
}
