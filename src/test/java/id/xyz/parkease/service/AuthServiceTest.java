package id.xyz.parkease.service;

import id.xyz.parkease.config.JwtProperties;
import id.xyz.parkease.domain.CustomerAccount;
import id.xyz.parkease.domain.CustomerAccount.AccountStatus;
import id.xyz.parkease.domain.Session;
import id.xyz.parkease.dto.AuthResponse;
import id.xyz.parkease.dto.LoginRequest;
import id.xyz.parkease.dto.RegisterRequest;
import id.xyz.parkease.exception.BusinessValidationException;
import id.xyz.parkease.exception.UnauthorizedException;
import id.xyz.parkease.repository.CustomerAccountRepository;
import id.xyz.parkease.repository.SessionRepository;
import id.xyz.parkease.security.DeviceInfoParser;
import id.xyz.parkease.security.JwtTokenService;
import id.xyz.parkease.security.RefreshTokenGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        AuthService.class,
        JwtTokenService.class,
        RefreshTokenGenerator.class,
        DeviceInfoParser.class,
        AuthServiceTest.TestBeans.class})
class AuthServiceTest {

    private static final String USERNAME = "driver1";
    private static final String PASSWORD = "correct-password";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36";
    private static final String IP_ADDRESS = "203.0.113.10";

    @Autowired
    private AuthService authService;

    @Autowired
    private CustomerAccountRepository customerAccountRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @BeforeEach
    void cleanDatabase() {
        sessionRepository.deleteAll();
        customerAccountRepository.deleteAll();
    }

    @Test
    void registerCreatesActiveCustomerAccountAndSession() {
        AuthResponse response = authService.register(new RegisterRequest(USERNAME, PASSWORD), USER_AGENT, IP_ADDRESS);

        assertNotNull(response.accessToken());
        assertNotNull(response.refreshToken());
        assertTrue(customerAccountRepository.existsByUsername(USERNAME));
        assertEquals(1, sessionRepository.count());
    }

    @Test
    void registerRejectsDuplicateUsername() {
        authService.register(new RegisterRequest(USERNAME, PASSWORD), USER_AGENT, IP_ADDRESS);

        assertThrows(
                BusinessValidationException.class,
                () -> authService.register(new RegisterRequest(USERNAME, PASSWORD), USER_AGENT, IP_ADDRESS));
    }

    @Test
    void loginWithCorrectPasswordSucceeds() {
        authService.register(new RegisterRequest(USERNAME, PASSWORD), USER_AGENT, IP_ADDRESS);

        AuthResponse response = authService.login(new LoginRequest(USERNAME, PASSWORD), USER_AGENT, IP_ADDRESS);

        assertNotNull(response.accessToken());
        assertNotNull(response.refreshToken());
    }

    @Test
    void loginWithWrongPasswordIsRejected() {
        authService.register(new RegisterRequest(USERNAME, PASSWORD), USER_AGENT, IP_ADDRESS);

        assertThrows(
                UnauthorizedException.class,
                () -> authService.login(new LoginRequest(USERNAME, "wrong-password"), USER_AGENT, IP_ADDRESS));
    }

    @Test
    void loginWithUnknownUsernameIsRejected() {
        assertThrows(
                UnauthorizedException.class,
                () -> authService.login(new LoginRequest("unknown-user", PASSWORD), USER_AGENT, IP_ADDRESS));
    }

    @Test
    void loginWithDisabledAccountIsRejected() {
        authService.register(new RegisterRequest(USERNAME, PASSWORD), USER_AGENT, IP_ADDRESS);
        CustomerAccount account = customerAccountRepository.findByUsername(USERNAME).orElseThrow();
        customerAccountRepository.save(account.toBuilder().status(AccountStatus.DISABLED).build());

        assertThrows(
                UnauthorizedException.class,
                () -> authService.login(new LoginRequest(USERNAME, PASSWORD), USER_AGENT, IP_ADDRESS));
    }

    @Test
    void refreshRotatesSessionAndIssuesNewRefreshToken() {
        AuthResponse initial = authService.register(new RegisterRequest(USERNAME, PASSWORD), USER_AGENT, IP_ADDRESS);

        AuthResponse refreshed = authService.refresh(initial.refreshToken(), USER_AGENT, IP_ADDRESS);

        assertNotEquals(initial.refreshToken(), refreshed.refreshToken());
        assertEquals(2, sessionRepository.count());
    }

    @Test
    void refreshRevokesThePreviousSessionAsRotated() {
        AuthResponse initial = authService.register(new RegisterRequest(USERNAME, PASSWORD), USER_AGENT, IP_ADDRESS);

        authService.refresh(initial.refreshToken(), USER_AGENT, IP_ADDRESS);

        assertThrows(
                UnauthorizedException.class,
                () -> authService.refresh(initial.refreshToken(), USER_AGENT, IP_ADDRESS));
    }

    @Test
    void refreshWithUnknownTokenIsRejected() {
        assertThrows(
                UnauthorizedException.class,
                () -> authService.refresh("unknown-refresh-token", USER_AGENT, IP_ADDRESS));
    }

    @Test
    void logoutRevokesSessionSoItCannotBeReused() {
        AuthResponse initial = authService.register(new RegisterRequest(USERNAME, PASSWORD), USER_AGENT, IP_ADDRESS);

        authService.logout(initial.refreshToken());

        assertThrows(
                UnauthorizedException.class,
                () -> authService.refresh(initial.refreshToken(), USER_AGENT, IP_ADDRESS));
    }

    @Test
    void logoutWithUnknownTokenIsRejected() {
        assertThrows(UnauthorizedException.class, () -> authService.logout("unknown-refresh-token"));
    }

    @Test
    void sessionRecordsDeviceAndIpInformation() {
        authService.register(new RegisterRequest(USERNAME, PASSWORD), USER_AGENT, IP_ADDRESS);

        Session session = sessionRepository.findAll().get(0);

        assertEquals("Chrome", session.getBrowserFamily());
        assertEquals(IP_ADDRESS, session.getIpAddress());
    }

    @Test
    void refreshIsRejectedWhenAccountIsDisabled() {
        AuthResponse initial = authService.register(new RegisterRequest(USERNAME, PASSWORD), USER_AGENT, IP_ADDRESS);
        CustomerAccount account = customerAccountRepository.findByUsername(USERNAME).orElseThrow();
        customerAccountRepository.save(account.toBuilder().status(AccountStatus.DISABLED).build());

        assertThrows(
                UnauthorizedException.class,
                () -> authService.refresh(initial.refreshToken(), USER_AGENT, IP_ADDRESS));
    }

    @Test
    void disableAccountRevokesActiveSessionsSoRefreshFails() {
        AuthResponse initial = authService.register(new RegisterRequest(USERNAME, PASSWORD), USER_AGENT, IP_ADDRESS);
        CustomerAccount account = customerAccountRepository.findByUsername(USERNAME).orElseThrow();

        authService.disableAccount(account.getId());

        assertEquals(AccountStatus.DISABLED, customerAccountRepository.findById(account.getId()).orElseThrow().getStatus());
        assertThrows(
                UnauthorizedException.class,
                () -> authService.refresh(initial.refreshToken(), USER_AGENT, IP_ADDRESS));
    }

    @Test
    void loginFailuresReturnUniformMessageAcrossUnknownWrongAndDisabled() {
        authService.register(new RegisterRequest(USERNAME, PASSWORD), USER_AGENT, IP_ADDRESS);
        CustomerAccount account = customerAccountRepository.findByUsername(USERNAME).orElseThrow();
        customerAccountRepository.save(account.toBuilder().status(AccountStatus.DISABLED).build());

        String unknownMessage = assertThrows(UnauthorizedException.class,
                () -> authService.login(new LoginRequest("nobody", PASSWORD), USER_AGENT, IP_ADDRESS)).getMessage();
        String disabledMessage = assertThrows(UnauthorizedException.class,
                () -> authService.login(new LoginRequest(USERNAME, PASSWORD), USER_AGENT, IP_ADDRESS)).getMessage();
        String wrongPasswordMessage = assertThrows(UnauthorizedException.class,
                () -> authService.login(new LoginRequest("nobody", "whatever"), USER_AGENT, IP_ADDRESS)).getMessage();

        assertEquals(unknownMessage, disabledMessage);
        assertEquals(unknownMessage, wrongPasswordMessage);
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2024-01-15T08:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties("test-secret-value-needs-to-be-at-least-32-bytes-long-for-hs256", 15, 7);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
