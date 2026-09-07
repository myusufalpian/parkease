package id.xyz.parkease.security;

import id.xyz.parkease.config.JwtProperties;
import id.xyz.parkease.domain.CustomerAccount;
import id.xyz.parkease.domain.CustomerAccount.Role;
import id.xyz.parkease.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenService {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey signingKey;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public JwtTokenService(JwtProperties jwtProperties, Clock clock) {
        this.jwtProperties = Objects.requireNonNull(jwtProperties);
        this.clock = Objects.requireNonNull(clock);
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(CustomerAccount account) {
        Instant now = clock.instant();
        Instant expiry = now.plusSeconds(jwtProperties.accessTokenTtlMinutes() * 60);
        return Jwts.builder()
                .subject(account.getId().toString())
                .claim(ROLE_CLAIM, account.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public AccessTokenClaims parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UUID customerAccountId = UUID.fromString(claims.getSubject());
            String roleClaim = claims.get(ROLE_CLAIM, String.class);
            if (roleClaim == null) {
                throw new IllegalArgumentException("missing role claim");
            }
            Role role = Role.valueOf(roleClaim);
            return new AccessTokenClaims(customerAccountId, role);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new UnauthorizedException("access token is invalid or expired");
        }
    }

    public record AccessTokenClaims(UUID customerAccountId, Role role) {
    }
}
