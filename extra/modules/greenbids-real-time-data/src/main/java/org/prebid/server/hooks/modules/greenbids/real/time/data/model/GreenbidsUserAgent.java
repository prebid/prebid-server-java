package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

//import com.iab.openrtb.request.UserAgent;
import ua_parser.Client;
import ua_parser.Parser;
import ua_parser.UserAgent;

import java.util.Set;

public class GreenbidsUserAgent {
    public static final Set<String> PC_OS_FAMILIES = Set.of(
            "Windows 95", "Windows 98", "Solaris");

    private final String userAgentString;
    private final UserAgent userAgent;
    private final ua_parser.Device device;
    private final ua_parser.OS os;

    private static final Parser uaParser = new Parser();

    public GreenbidsUserAgent(String userAgentString) {
        this.userAgentString = userAgentString;
        Client client = uaParser.parse(userAgentString);
        this.userAgent = client.userAgent;
        this.device = client.device;
        this.os = client.os;
    }

    public String getDevice() {
        return isPC() ? "PC" : device.family;
    }

    public String getBrowser() {
        return String.format("%s %s", userAgent.family, userAgent.major).trim();
    }

    public boolean isPC() {
        return userAgentString.contains("Windows NT") ||
                PC_OS_FAMILIES.contains(os.family) ||
                ("Windows".equals(os.family) && "ME".equals(os.major)) ||
                ("Mac OS X".equals(os.family) && !userAgentString.contains("Silk")) ||
                userAgentString.contains("Linux") && userAgentString.contains("X11");
    }
}
