package org.prebid.server.hooks.modules.greenbids.real.time.data.model.data;

import ua_parser.Client;
import ua_parser.Device;
import ua_parser.OS;
import ua_parser.Parser;
import ua_parser.UserAgent;

import java.util.Optional;
import java.util.Set;

public class GreenbidsUserAgent {

    public static final Set<String> PC_OS_FAMILIES = Set.of(
            "Windows 95", "Windows 98", "Solaris");

    private static final Parser UA_PARSER = new Parser();

    private final Optional<String> userAgentString;

    private final Optional<UserAgent> userAgent;

    private final Optional<Device> device;

    private final Optional<OS> os;

    public GreenbidsUserAgent(String userAgentString) {
        this.userAgentString = Optional.ofNullable(userAgentString);
        final Client client = UA_PARSER.parse(userAgentString);
        this.userAgent = Optional.ofNullable(client.userAgent);
        this.device = Optional.ofNullable(client.device);
        this.os = Optional.ofNullable(client.os);
    }

    public String getDevice() {
        return device.map(device -> isPC() ? "PC" : device.family).orElse("");
    }

    public String getBrowser() {
        return userAgent
                .map(userAgent -> "%s %s".formatted(userAgent.family, userAgent.major).trim())
                .orElse("");
    }

    private boolean isPC() {
        return userAgentString
                .map(userAgent -> userAgent.contains("Windows NT")
                        || PC_OS_FAMILIES.contains(osFamily())
                        || ("Windows".equals(osFamily()) && "ME".equals(osMajor()))
                        || ("Mac OS X".equals(osFamily()) && !userAgent.contains("Silk"))
                        || userAgent.contains("Linux") && userAgent.contains("X11"))
                .orElse(false);
    }

    private String osFamily() {
        return os.map(os -> os.family).orElse("");
    }

    private String osMajor() {
        return os.map(os -> os.major).orElse("");
    }
}
