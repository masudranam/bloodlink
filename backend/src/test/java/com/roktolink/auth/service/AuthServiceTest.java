package com.roktolink.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.roktolink.auth.LoginRequest;
import com.roktolink.auth.LoginResponse;
import com.roktolink.auth.MeResponse;
import com.roktolink.auth.RegisterRequest;
import com.roktolink.auth.RegisterResponse;
import com.roktolink.auth.jwt.JwtIssuer;
import com.roktolink.user.AppUser;
import com.roktolink.user.AppUserRepository;
import com.roktolink.user.UserRole;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Tests for the parts of authentication where being wrong is expensive and the
 * behaviour is not obvious from reading the code: that the stored password is a
 * hash, that phone numbers collapse to one canonical form, and that a failed
 * login is indistinguishable whichever half was wrong.
 *
 * <p>Method names carry the acceptance criterion they cover, so
 * {@code grep -rn ac5_ backend/src/test} finds the evidence for AC-5.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String VALID_PASSWORD = "correct-horse-battery";

    @Mock
    private AppUserRepository users;

    @Mock
    private JwtIssuer jwtIssuer;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(users, passwordEncoder, jwtIssuer);
    }

    // ---------- AC-1: registration stores a BCrypt hash, never the password ----------

    @Test
    void ac1_register_storesBcryptHashAndNeverThePlaintext() {
        when(users.existsByPhone("+8801712345678")).thenReturn(false);
        when(users.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        RegisterResponse response = authService.register(new RegisterRequest(
                "Masud Rana", "01712345678", VALID_PASSWORD, UserRole.DONOR));

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(saved.capture());
        String hash = saved.getValue().getPasswordHash();

        assertThat(hash).isNotEqualTo(VALID_PASSWORD);
        assertThat(hash).hasSize(60);
        assertThat(hash).matches("^\\$2[aby]\\$.*");
        assertThat(passwordEncoder.matches(VALID_PASSWORD, hash)).isTrue();
        assertThat(response.fullName()).isEqualTo("Masud Rana");
        assertThat(response.role()).isEqualTo(UserRole.DONOR);
    }

    @Test
    void ac1_register_storesThePhoneCanonicalised() {
        when(users.existsByPhone("+8801712345678")).thenReturn(false);
        when(users.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        authService.register(new RegisterRequest(
                "Masud Rana", "  0171-234 5678 ", VALID_PASSWORD, UserRole.REQUESTER));

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getPhone()).isEqualTo("+8801712345678");
    }

    // ---------- AC-2: one person cannot hold two accounts ----------

    @Test
    void ac2_register_rejectsAnAlreadyRegisteredPhone() {
        when(users.existsByPhone("+8801712345678")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
                "Impostor", "+8801712345678", VALID_PASSWORD, UserRole.DONOR)))
                .isInstanceOf(DuplicatePhoneException.class)
                .hasMessage("Phone already registered");

        verify(users, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"01712345678", "+8801712345678", "8801712345678", "1712345678"})
    void ac2_everyAcceptedPhoneForm_normalisesToOneStoredValue(String typed) {
        assertThat(PhoneNumber.normalise(typed)).isEqualTo("+8801712345678");
    }

    @ParameterizedTest
    @ValueSource(strings = {"01212345678", "0171234567", "017123456789", "+8802712345678",
        "not-a-number", "", "+880171234567a"})
    void ac3_aNumberThatIsNotABangladeshiMobile_isRejected(String typed) {
        assertThatThrownBy(() -> PhoneNumber.normalise(typed))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ac3_aNullPhone_isRejected() {
        assertThatThrownBy(() -> PhoneNumber.normalise(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Phone number is required");
    }

    // ---------- AC-4: a good login yields a token ----------

    @Test
    void ac4_login_returnsATokenItsTypeLifetimeAndRole() {
        AppUser user = existingDonor();
        when(users.findByPhone("+8801712345678")).thenReturn(Optional.of(user));
        when(jwtIssuer.issue(user)).thenReturn("signed.jwt.value");
        when(jwtIssuer.ttlSeconds()).thenReturn(43200L);

        LoginResponse response = authService.login(new LoginRequest("01712345678", VALID_PASSWORD));

        assertThat(response.accessToken()).isEqualTo("signed.jwt.value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(43200L);
        assertThat(response.role()).isEqualTo(UserRole.DONOR);
    }

    // ---------- AC-5: a failed login gives nothing away ----------

    @Test
    void ac5_loginWithAnUnknownPhone_failsIdenticallyToAWrongPassword() {
        when(users.findByPhone("+8801700000000")).thenReturn(Optional.empty());
        when(users.findByPhone("+8801712345678")).thenReturn(Optional.of(existingDonor()));

        Throwable unknownPhone = catchLoginFailure("01700000000", VALID_PASSWORD);
        Throwable wrongPassword = catchLoginFailure("01712345678", "not-the-password");

        assertThat(unknownPhone).isInstanceOf(InvalidCredentialsException.class);
        assertThat(wrongPassword).isInstanceOf(InvalidCredentialsException.class);
        assertThat(unknownPhone.getMessage()).isEqualTo(wrongPassword.getMessage());
        assertThat(unknownPhone.getClass()).isEqualTo(wrongPassword.getClass());
    }

    @Test
    void ac5_loginWithAnUnparseablePhone_failsTheSameWayAsAnUnknownOne() {
        Throwable malformed = catchLoginFailure("definitely-not-a-phone", VALID_PASSWORD);

        assertThat(malformed).isInstanceOf(InvalidCredentialsException.class);
        assertThat(malformed).hasMessage("Invalid phone or password");
        verify(users, never()).findByPhone(any());
    }

    // ---------- AC-6 / AC-9: identity carries no phone number ----------

    @Test
    void ac6_me_returnsTheTokenHolderWithoutTheirPhone() {
        when(users.findById(7L)).thenReturn(Optional.of(existingDonor()));

        MeResponse response = authService.me(7L);

        assertThat(response.fullName()).isEqualTo("Masud Rana");
        assertThat(response.role()).isEqualTo(UserRole.DONOR);
        assertThat(MeResponse.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("phone"));
    }

    @Test
    void ac6_me_rejectsATokenWhoseAccountIsGone() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.me(99L))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void ac9_noAuthResponseCarriesAPhoneField() {
        assertThat(RegisterResponse.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("phone"));
        assertThat(LoginResponse.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("phone"));
        assertThat(MeResponse.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("phone"));
    }

    private Throwable catchLoginFailure(String phone, String password) {
        try {
            authService.login(new LoginRequest(phone, password));
            throw new AssertionError("expected the login to fail");
        } catch (InvalidCredentialsException expected) {
            return expected;
        }
    }

    private AppUser existingDonor() {
        return new AppUser("Masud Rana", "+8801712345678",
                passwordEncoder.encode(VALID_PASSWORD), UserRole.DONOR);
    }
}
