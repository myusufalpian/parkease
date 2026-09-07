package id.xyz.parkease.service;

import id.xyz.parkease.config.JwtProperties;
import id.xyz.parkease.domain.CustomerAccount;
import id.xyz.parkease.domain.CustomerAccount.AccountStatus;
import id.xyz.parkease.domain.Session;
import id.xyz.parkease.domain.Session.RevokeReason;
import id.xyz.parkease.dto.AuthResponse;
import id.xyz.parkease.dto.LoginRequest;
import id.xyz.parkease.dto.RegisterRequest;
import id.xyz.parkease.exception.BusinessValidationException;
import id.xyz.parkease.exception.ResourceNotFoundException;
import id.xyz.parkease.exception.UnauthorizedException;
import id.xyz.parkease.repository.CustomerAccountRepository;
import id.xyz.parkease.repository.SessionRepository;
import id.xyz.parkease.security.DeviceInfoParser;
import id.xyz.parkease.security.DeviceInfoParser.DeviceInfo;
import id.xyz.parkease.security.JwtTokenService;
import id.xyz.parkease.security.RefreshTokenGenerator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private static final String INVALID_CREDENTIALS = "invalid username or password";

    private final CustomerAccountRepository customerAccountRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final DeviceInfoParser deviceInfoParser;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AuthService(
            CustomerAccountRepository customerAccountRepository,
            SessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            RefreshTokenGenerator refreshTokenGenerator,
            DeviceInfoParser deviceInfoParser,
            JwtProperties jwtProperties,
            Clock clock) {
        this.customerAccountRepository = Objects.requireNonNull(customerAccountRepository);
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.jwtTokenService = Objects.requireNonNull(jwtTokenService);
        this.refreshTokenGenerator = Objects.requireNonNull(refreshTokenGenerator);
        this.deviceInfoParser = Objects.requireNonNull(deviceInfoParser);
        this.jwtProperties = Objects.requireNonNull(jwtProperties);
        this.clock = Objects.requireNonNull(clock);
    }

    public AuthResponse register(RegisterRequest request, String userAgent, String ipAddress) {
        if (customerAccountRepository.existsByUsername(request.username())) {
            throw new BusinessValidationException("username is already registered");
        }
        CustomerAccount account = CustomerAccount.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();
        CustomerAccount savedAccount = customerAccountRepository.save(account);
        return issueTokens(savedAccount, userAgent, ipAddress);
    }

    public AuthResponse login(LoginRequest request, String userAgent, String ipAddress) {
        CustomerAccount account = customerAccountRepository.findByUsername(request.username())
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));
        if (account.getStatus() != AccountStatus.ACTIVE
                || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        return issueTokens(account, userAgent, ipAddress);
    }

    public AuthResponse refresh(String rawRefreshToken, String userAgent, String ipAddress) {
        Session session = claimActiveSession(rawRefreshToken, RevokeReason.ROTATED);
        CustomerAccount account = session.getCustomerAccount();
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new UnauthorizedException("refresh token is invalid");
        }
        return issueTokens(account, userAgent, ipAddress);
    }

    public void logout(String rawRefreshToken) {
        claimActiveSession(rawRefreshToken, RevokeReason.LOGOUT);
    }

    public void disableAccount(UUID customerAccountId) {
        CustomerAccount account = customerAccountRepository.findById(customerAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("customer account was not found"));
        customerAccountRepository.save(account.toBuilder().status(AccountStatus.DISABLED).build());
        sessionRepository.revokeAllActiveForAccount(customerAccountId, RevokeReason.LOGOUT.name(), currentTime());
    }

    private AuthResponse issueTokens(CustomerAccount account, String userAgent, String ipAddress) {
        String accessToken = jwtTokenService.issueAccessToken(account);
        String rawRefreshToken = refreshTokenGenerator.generateRawToken();
        OffsetDateTime now = currentTime();
        DeviceInfo deviceInfo = deviceInfoParser.parse(userAgent);
        Session session = Session.builder()
                .customerAccount(account)
                .refreshTokenHash(refreshTokenGenerator.hash(rawRefreshToken))
                .issuedAt(now)
                .expiresAt(now.plusDays(jwtProperties.refreshTokenTtlDays()))
                .userAgent(userAgent)
                .deviceFamily(deviceInfo.deviceFamily())
                .osFamily(deviceInfo.osFamily())
                .browserFamily(deviceInfo.browserFamily())
                .ipAddress(ipAddress)
                .build();
        sessionRepository.save(session);
        long expiresInSeconds = jwtProperties.accessTokenTtlMinutes() * 60;
        return new AuthResponse(accessToken, rawRefreshToken, expiresInSeconds);
    }

    private Session claimActiveSession(String rawRefreshToken, RevokeReason reason) {
        String refreshTokenHash = refreshTokenGenerator.hash(rawRefreshToken);
        int claimed = sessionRepository.revokeActiveByHash(refreshTokenHash, reason.name(), currentTime());
        if (claimed != 1) {
            throw new UnauthorizedException("refresh token is invalid");
        }
        return sessionRepository.findByRefreshTokenHash(refreshTokenHash)
                .orElseThrow(() -> new UnauthorizedException("refresh token is invalid"));
    }

    private OffsetDateTime currentTime() {
        return OffsetDateTime.now(clock);
    }
}
