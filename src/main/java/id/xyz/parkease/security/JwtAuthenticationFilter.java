package id.xyz.parkease.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import id.xyz.parkease.exception.ErrorResponse;
import id.xyz.parkease.exception.UnauthorizedException;
import id.xyz.parkease.domain.CustomerAccount;
import id.xyz.parkease.repository.CustomerAccountRepository;
import id.xyz.parkease.security.JwtTokenService.AccessTokenClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String PRINCIPAL_ATTRIBUTE = "parkease.authenticatedPrincipal";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";
    private static final Set<String> PUBLIC_ACTUATOR_PATHS = Set.of("/actuator/health", "/actuator/info");
    // Public availability endpoint: GET /api/v1/lots/{lotId}/availability
    private static final Pattern AVAILABILITY_PATTERN =
            Pattern.compile("^/api/v1/lots/[^/]+/availability$");

    private final JwtTokenService jwtTokenService;
    private final CustomerAccountRepository customerAccountRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, CustomerAccountRepository customerAccountRepository) {
        this.jwtTokenService = Objects.requireNonNull(jwtTokenService);
        this.customerAccountRepository = Objects.requireNonNull(customerAccountRepository);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = normalize(request.getRequestURI());
        String method = request.getMethod();
        if (uri.startsWith(AUTH_PATH_PREFIX) && "POST".equalsIgnoreCase(method)) {
            return true;
        }
        if (PUBLIC_ACTUATOR_PATHS.contains(uri) && "GET".equalsIgnoreCase(method)) {
            return true;
        }
        return "GET".equalsIgnoreCase(method) && AVAILABILITY_PATTERN.matcher(uri).matches();
    }

    // Defense-in-depth against trailing-slash/matrix/duplicate-slash variants so
    // a normalized public route cannot be shifted onto the protected filter path
    // (or vice versa) by path tricks.
    private String normalize(String uri) {
        String path = uri;
        int matrix = path.indexOf(';');
        if (matrix >= 0) {
            path = path.substring(0, matrix);
        }
        path = path.replaceAll("/{2,}", "/");
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            request.setAttribute(PRINCIPAL_ATTRIBUTE, resolvePrincipal(request));
        } catch (UnauthorizedException exception) {
            writeUnauthorized(response, exception.getMessage());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private AuthenticatedPrincipal resolvePrincipal(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("missing or malformed Authorization header");
        }
        String token = header.substring(BEARER_PREFIX.length());
        AccessTokenClaims claims = jwtTokenService.parseAccessToken(token);
        CustomerAccount account = customerAccountRepository.findById(claims.customerAccountId())
                .orElseThrow(() -> new UnauthorizedException("account is not available"));
        if (account.getStatus() != CustomerAccount.AccountStatus.ACTIVE) {
            throw new UnauthorizedException("account is disabled");
        }
        return new AuthenticatedPrincipal(account.getId(), account.getRole());
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(OffsetDateTime.now(), HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
