package id.xyz.parkease.security;

import id.xyz.parkease.config.JwtProperties;
import id.xyz.parkease.domain.CustomerAccount;
import id.xyz.parkease.domain.CustomerAccount.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-value-needs-to-be-at-least-32-bytes-long-for-hs256";
    private static final Instant FIXED_INSTANT = Instant.parse("2024-01-15T08:00:00Z");

    private static final class RecordingFilterChain implements FilterChain {
        private boolean invoked;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            invoked = true;
        }
    }

    private JwtTokenService jwtTokenService() {
        Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        return new JwtTokenService(new JwtProperties(SECRET, 15, 7), fixedClock);
    }

    private CustomerAccount account() {
        return CustomerAccount.builder().username("driver1").passwordHash("hash").role(Role.CUSTOMER).build();
    }

    @Test
    void validBearerTokenSetsPrincipalAttributeAndContinuesChain() throws ServletException, IOException {
        JwtTokenService jwtTokenService = jwtTokenService();
        CustomerAccount account = account();
        String token = jwtTokenService.issueAccessToken(account);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reservations");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        assertTrue(filterChain.invoked);
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) request.getAttribute(JwtAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        assertEquals(account.getId(), principal.customerAccountId());
        assertEquals(Role.CUSTOMER, principal.role());
    }

    @Test
    void missingAuthorizationHeaderReturns401AndDoesNotContinueChain() throws ServletException, IOException {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reservations");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        assertFalse(filterChain.invoked);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("UNAUTHORIZED"));
    }

    @Test
    void malformedAuthorizationHeaderReturns401() throws ServletException, IOException {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reservations");
        request.addHeader("Authorization", "not-bearer-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        assertFalse(filterChain.invoked);
        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldNotFilterAllowsPublicRoutes() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService());

        MockHttpServletRequest authRequest = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletRequest availabilityRequest = new MockHttpServletRequest("GET", "/api/v1/lots/" + UUID.randomUUID() + "/availability");
        MockHttpServletRequest actuatorHealth = new MockHttpServletRequest("GET", "/actuator/health");

        assertTrue(filter.shouldNotFilter(authRequest));
        assertTrue(filter.shouldNotFilter(availabilityRequest));
        assertTrue(filter.shouldNotFilter(actuatorHealth));
    }

    @Test
    void shouldFilterProtectedRoutes() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService());
        MockHttpServletRequest bookingRequest = new MockHttpServletRequest("POST", "/api/v1/reservations");

        assertFalse(filter.shouldNotFilter(bookingRequest));
    }
}
