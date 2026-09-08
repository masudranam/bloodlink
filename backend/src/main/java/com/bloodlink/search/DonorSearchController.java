package com.bloodlink.search;

import com.bloodlink.request.PageResponse;
import com.bloodlink.search.service.DonorSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Donor search, mounted under the request it searches for.
 *
 * <p>There is no free-form {@code /api/donors?bloodGroup=&hospitalId=}. Hanging
 * the search off a request the caller owns fixes the patient's group, the
 * hospital and the ownership check in one move, and leaves nowhere for someone
 * with no request at all to probe the donor list.
 */
@RestController
@RequestMapping("/api/requests/{requestId}/donors")
@Validated
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Donor search",
        description = "Compatible, eligible, available and nearby donors for one request")
public class DonorSearchController {

    private final DonorSearchService search;

    public DonorSearchController(DonorSearchService search) {
        this.search = search;
    }

    /**
     * The donors who could serve this request, nearest first.
     *
     * @param token     the caller's token
     * @param requestId the request to search against
     * @param radiusKm  how far from the hospital to look, 1 to 50
     * @param page      zero-based page number
     * @param size      page size, 1 to 100
     * @return a page of matches, none of them carrying a phone number
     */
    @GetMapping
    @Operation(summary = "Find donors for a request",
            description = "Compatible with the patient's group, eligible today, available, and within "
                    + "radiusKm of the hospital. Ranked nearest first. No response here carries a "
                    + "phone number: that is revealed only after a pledge is accepted.")
    @ApiResponse(responseCode = "200", description = "A page of matches, possibly empty")
    @ApiResponse(responseCode = "400", description = "A parameter is outside its bounds", content = @Content)
    @ApiResponse(responseCode = "403", description = "Not a REQUESTER, or not your request", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such request", content = @Content)
    @ApiResponse(responseCode = "409", description = "The request has reached a terminal status",
            content = @Content)
    public PageResponse<DonorMatch> search(
            @AuthenticationPrincipal Jwt token,
            @PathVariable long requestId,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "must be at least 1")
            @Max(value = 50, message = "must be at most 50") int radiusKm,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "must be at least 1")
            @Max(value = 100, message = "must be at most 100") int size) {
        return search.search(Long.parseLong(token.getSubject()), requestId, radiusKm, page, size);
    }
}
