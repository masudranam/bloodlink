package com.bloodlink.auth.service;

import com.bloodlink.auth.LoginRequest;
import com.bloodlink.auth.LoginResponse;
import com.bloodlink.auth.MeResponse;
import com.bloodlink.auth.RegisterRequest;
import com.bloodlink.auth.RegisterResponse;
import com.bloodlink.auth.jwt.JwtIssuer;
import com.bloodlink.user.AppUser;
import com.bloodlink.user.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and login.
 *
 * <p>Two properties matter more than anything else here. A password is hashed
 * before it is stored and is never written anywhere else, and a failed login says
 * only that it failed — the same exception and the same response body whether the
 * phone is unknown or the password is wrong, so the endpoint cannot be used to
 * enumerate who has an account.
 */
@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtIssuer jwtIssuer) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
    }

    /**
     * Creates an account.
     *
     * @param request the submitted registration
     * @return the new user's identity, without their phone number
     * @throws DuplicatePhoneException if the normalised phone already has an account
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String phone = PhoneNumber.normalise(request.phone());
        if (users.existsByPhone(phone)) {
            throw new DuplicatePhoneException("Phone already registered");
        }

        AppUser user = new AppUser(
                request.fullName().trim(),
                phone,
                passwordEncoder.encode(request.password()),
                request.role());

        AppUser saved = users.save(user);
        return new RegisterResponse(saved.getId(), saved.getFullName(), saved.getRole());
    }

    /**
     * Exchanges credentials for a token.
     *
     * @param request the submitted credentials
     * @return a signed access token and its lifetime
     * @throws InvalidCredentialsException if the phone is unknown, unparseable, or
     *                                     the password does not match
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String phone;
        try {
            phone = PhoneNumber.normalise(request.phone());
        } catch (IllegalArgumentException malformed) {
            // A number that cannot be a phone is treated exactly like a number that
            // simply is not registered. Anything else would leak.
            throw new InvalidCredentialsException();
        }

        AppUser user = users.findByPhone(phone).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return new LoginResponse(
                jwtIssuer.issue(user),
                "Bearer",
                jwtIssuer.ttlSeconds(),
                user.getRole());
    }

    /**
     * Resolves the holder of a token.
     *
     * @param userId the token's subject
     * @return the user's identity, without their phone number
     * @throws InvalidCredentialsException if the user no longer exists — a token
     *                                     outliving its account is not an identity
     */
    @Transactional(readOnly = true)
    public MeResponse me(long userId) {
        AppUser user = users.findById(userId).orElseThrow(InvalidCredentialsException::new);
        return new MeResponse(user.getId(), user.getFullName(), user.getRole());
    }
}
