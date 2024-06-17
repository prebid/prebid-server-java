package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

//import com.iab.openrtb.request.UserAgent;
import ua_parser.Client;
import ua_parser.Parser;
import ua_parser.UserAgent;

public class GreenbidsUserAgent {
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

    @Override
    public String toString() {
        return String.format("%s / %s / %s", getDevice(), getOs(), getBrowser());
    }

    public String getDevice() {
        return isPC() ? "PC" : device.family;
    }

    public String getOs() {
        return String.format("%s %s", os.family, os.major).trim();
    }

    public String getBrowser() {
        return String.format("%s %s", userAgent.family, userAgent.major).trim();
    }

    public boolean isPC() {
        return userAgentString.contains("Windows NT") ||
                UserAgentUtils.PC_OS_FAMILIES.contains(os.family) ||
                ("Windows".equals(os.family) && "ME".equals(os.major)) ||
                ("Mac OS X".equals(os.family) && !userAgentString.contains("Silk")) ||
                userAgentString.contains("Linux") && userAgentString.contains("X11");
    }

    public boolean isTablet() {
        return UserAgentUtils.TABLET_DEVICE_FAMILIES.contains(device.family) ||
                ("Android".equals(os.family) && isAndroidTablet()) ||
                ("Windows".equals(os.family) && os.major.startsWith("RT")) ||
                ("Firefox OS".equals(os.family) && !"Mobile".equals(userAgent.family));
    }

    public boolean isMobile() {
        return UserAgentUtils.MOBILE_DEVICE_FAMILIES.contains(device.family) ||
                UserAgentUtils.MOBILE_BROWSER_FAMILIES.contains(userAgent.family) ||
                (("Android".equals(os.family) || "Firefox OS".equals(os.family)) && !isTablet()) ||
                ("BlackBerry OS".equals(os.family) && !"Blackberry Playbook".equals(device.family)) ||
                UserAgentUtils.MOBILE_OS_FAMILIES.contains(os.family) ||
                userAgentString.contains("J2ME") || userAgentString.contains("MIDP") ||
                userAgentString.contains("iPhone;") || userAgentString.contains("Googlebot-Mobile") ||
                ("Spider".equals(device.family) && userAgent.family.contains("Mobile")) ||
                (userAgentString.contains("NokiaBrowser") && userAgentString.contains("Mobile"));
    }

    public boolean isTouchCapable() {
        return UserAgentUtils.TOUCH_CAPABLE_OS_FAMILIES.contains(os.family) ||
                UserAgentUtils.TOUCH_CAPABLE_DEVICE_FAMILIES.contains(device.family) ||
                ("Windows".equals(os.family) && (os.major.startsWith("RT") || os.major.startsWith("CE") ||
                        (os.major.startsWith("8") && userAgentString.contains("Touch")))) ||
                ("BlackBerry".equals(os.family) && isBlackberryTouchCapableDevice());
    }

    public boolean isBot() {
        return "Spider".equals(device.family);
    }

    public boolean isEmailClient() {
        return UserAgentUtils.EMAIL_PROGRAM_FAMILIES.contains(userAgent.family);
    }

    private boolean isAndroidTablet() {
        return !userAgentString.contains("Mobile Safari") && !"Firefox Mobile".equals(userAgent.family);
    }

    private boolean isBlackberryTouchCapableDevice() {
        return device.family.startsWith("Blackberry 99") || device.family.startsWith("Blackberry 95");
    }
}
