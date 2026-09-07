package id.xyz.parkease.security;

import id.xyz.parkease.config.JwtProperties;
import id.xyz.parkease.domain.CustomerAccount;
import id.xyz.parkease.domain.CustomerAccount.Role;
import id.xyz.parkease.exception.UnauthorizedException;
import id.xyz.parkease.security.JwtTokenService.AccessTokenClaims;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-value-needs-to-be-at-least-32-bytes-long-for-hs256";
    private static final long ACCESS_TOKEN_TTL_MINUTES = 15;
    private static final long REFRESH_TOKEN_TTL_DAYS = 7;
    private static final Instant FIXED_INSTANT = Instant.parse("2024-01-15T08:00:00Z");

    private Clock fixedClockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private JwtTokenService service(Clock clock) {
        return new JwtTokenService(new JwtProperties(SECRET, ACCESS_TOKEN_TTL_MINUTES, REFRESH_TOKEN_TTL_DAYS), clock);
    }

    private CustomerAccount account() {
        return CustomerAccount.builder().username("driver1").passwordHash("hash").role(Role.OPERATOR).build();
    }

    @Test
    void issueThenParseRoundTripsCustomerAccountIdAndRole() {
        JwtTokenService service = service(fixedClockAt(FIXED_INSTANT));
        CustomerAccount account = account();

        String token = service.issueAccessToken(account);
        AccessTokenClaims claims = service.parseAccessToken(token);

        assertEquals(account.getId(), claims.customerAccountId());
        assertEquals(Role.OPERATOR, claims.role());
    }

    @Test
    void parseAcceptsTokenOneSecondBeforeExpiry() {
        JwtTokenService issuer = service(fixedClockAt(FIXED_INSTANT));
        CustomerAccount account = account();
        String token = issuer.issueAccessToken(account);
        Instant almostExpired = FIXED_INSTANT.plusSeconds(ACCESS_TOKEN_TTL_MINUTES * 60 - 1);

        JwtTokenService parserAtAlmostExpired = service(fixedClockAt(almostExpired));
        AccessTokenClaims claims = parserAtAlmostExpired.parseAccessToken(token);

        assertEquals(account.getId(), claims.customerAccountId());
    }

    @Test
    void parseRejectsExpiredToken() {
        JwtTokenService issuer = service(fixedClockAt(FIXED_INSTANT));
        String token = issuer.issueAccessToken(account());
        Instant afterExpiry = FIXED_INSTANT.plusSeconds(ACCESS_TOKEN_TTL_MINUTES * 60 + 1);

        JwtTokenService parserAfterExpiry = service(fixedClockAt(afterExpiry));
        assertThrows(UnauthorizedException.class, () -> parserAfterExpiry.parseAccessToken(token));
    }

    @Test
    void parseRejectsTokenSignedWithDifferentSecret() {
        JwtTokenService issuedWithOtherSecret = new JwtTokenService(
                new JwtProperties("a-completely-different-secret-that-is-also-32-bytes-plus", ACCESS_TOKEN_TTL_MINUTES, REFRESH_TOKEN_TTL_DAYS),
                fixedClockAt(FIXED_INSTANT));
        String token = issuedWithOtherSecret.issueAccessToken(account());

        JwtTokenService parser = service(fixedClockAt(FIXED_INSTANT));
        assertThrows(UnauthorizedException.class, () -> parser.parseAccessToken(token));
    }

    @Test
    void parseRejectsMalformedToken() {
        JwtTokenService service = service(fixedClockAt(FIXED_INSTANT));
        assertThrows(UnauthorizedException.class, () -> service.parseAccessToken("not-a-jwt"));
    }
}
