package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import org.apache.commons.lang3.StringUtils;
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

    private final String userAgentString;

    private final UserAgent userAgent;

    private final Device device;

    private final OS os;

    public GreenbidsUserAgent(String userAgentString) {
        this.userAgentString = userAgentString;
        final Client client = UA_PARSER.parse(userAgentString);
        this.userAgent = client.userAgent;
        this.device = client.device;
        this.os = client.os;
    }

    public String getDevice() {
        return Optional.ofNullable(device)
                .map(device -> isPC() ? "PC" : device.family)
                .orElse(StringUtils.EMPTY);
    }

    public String getBrowser() {
        return Optional.ofNullable(userAgent)
                .filter(userAgent -> !"Other".equals(userAgent.family) && StringUtils.isNoneBlank(userAgent.family))
                .map(ua -> "%s %s".formatted(ua.family, StringUtils.defaultString(userAgent.major)).trim())
                .orElse(StringUtils.EMPTY);
    }

    private boolean isPC() {
        final String osFamily = osFamily();
        return Optional.ofNullable(userAgentString)
                .map(userAgent -> userAgent.contains("Windows NT")
                        || PC_OS_FAMILIES.contains(osFamily)
                        || ("Windows".equals(osFamily) && "ME".equals(osMajor()))
                        || ("Mac OS X".equals(osFamily) && !userAgent.contains("Silk"))
                        || (userAgent.contains("Linux") && userAgent.contains("X11")))
                .orElse(false);
    }

    private String osFamily() {
        return Optional.ofNullable(os).map(os -> os.family).orElse(StringUtils.EMPTY);
    }

    private String osMajor() {
        return Optional.ofNullable(os).map(os -> os.major).orElse(StringUtils.EMPTY);
    }
}
