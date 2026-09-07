package id.xyz.parkease.security;

import id.xyz.parkease.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final int MAX_KEYS = 10_000;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    public AuthRateLimiter(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized void check(String operation, String clientIp) {
        Instant now = clock.instant();
        cleanup(now);
        String key = operation + ":" + (clientIp == null ? "unknown" : clientIp);
        Window window = windows.computeIfAbsent(key, ignored -> new Window(now));
        if (!window.startedAt.plus(WINDOW).isAfter(now)) {
            window = new Window(now);
            windows.put(key, window);
        }
        if (window.attempts >= MAX_ATTEMPTS) {
            throw new TooManyRequestsException("too many authentication requests");
        }
        window.attempts++;
    }

    private void cleanup(Instant now) {
        Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().startedAt.plus(WINDOW).isBefore(now)) {
                iterator.remove();
            }
        }
        if (windows.size() >= MAX_KEYS) {
            windows.clear();
        }
    }

    private static final class Window {
        private final Instant startedAt;
        private int attempts;

        private Window(Instant startedAt) {
            this.startedAt = startedAt;
        }
    }
}
