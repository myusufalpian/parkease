package id.xyz.parkease.security;

import id.xyz.parkease.security.DeviceInfoParser.DeviceInfo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeviceInfoParserTest {

    private static final String CHROME_ON_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/117.0.0.0 Safari/537.36";

    private final DeviceInfoParser parser = new DeviceInfoParser();

    @Test
    void parsesKnownDesktopUserAgent() {
        DeviceInfo deviceInfo = parser.parse(CHROME_ON_WINDOWS);

        assertEquals("Windows", deviceInfo.osFamily());
        assertEquals("Chrome", deviceInfo.browserFamily());
    }

    @Test
    void blankUserAgentReturnsAllNullFields() {
        DeviceInfo deviceInfo = parser.parse("  ");

        assertNull(deviceInfo.deviceFamily());
        assertNull(deviceInfo.osFamily());
        assertNull(deviceInfo.browserFamily());
    }

    @Test
    void nullUserAgentReturnsAllNullFields() {
        DeviceInfo deviceInfo = parser.parse(null);

        assertNull(deviceInfo.deviceFamily());
        assertNull(deviceInfo.osFamily());
        assertNull(deviceInfo.browserFamily());
    }
}
