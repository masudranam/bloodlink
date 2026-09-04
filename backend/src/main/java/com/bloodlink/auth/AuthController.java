package com.bloodlink.auth;

import com.bloodlink.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Registration, login and identity. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Register, log in, and find out who you are")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account as a donor or a requester",
            description = "The response never contains a phone number.")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Invalid body", content = @Content)
    @ApiResponse(responseCode = "409", description = "Phone already registered", content = @Content)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange phone and password for a bearer token",
            description = "An unknown phone and a wrong password give the same 401.")
    @ApiResponse(responseCode = "200", description = "Token issued")
    @ApiResponse(responseCode = "401", description = "Invalid phone or password", content = @Content)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Who the current token belongs to")
    @ApiResponse(responseCode = "200", description = "The token holder")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content)
    public MeResponse me(@AuthenticationPrincipal Jwt token) {
        return authService.me(Long.parseLong(token.getSubject()));
    }
}
