package id.xyz.parkease.security;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ua_parser.Client;
import ua_parser.Parser;

@Component
public class DeviceInfoParser {

    private final Parser parser = new Parser();

    public DeviceInfo parse(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return new DeviceInfo(null, null, null);
        }
        Client client = parser.parse(userAgent);
        return new DeviceInfo(client.device.family, client.os.family, client.userAgent.family);
    }

    public record DeviceInfo(String deviceFamily, String osFamily, String browserFamily) {
    }
}
