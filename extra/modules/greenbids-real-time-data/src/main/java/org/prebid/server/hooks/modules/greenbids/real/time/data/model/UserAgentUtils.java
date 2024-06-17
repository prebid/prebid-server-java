package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import java.util.Set;

public class UserAgentUtils {
    public static final Set<String> MOBILE_DEVICE_FAMILIES = Set.of(
            "iPhone", "iPod", "Generic Smartphone", "Generic Feature Phone",
            "PlayStation Vita", "iOS-Device");

    public static final Set<String> PC_OS_FAMILIES = Set.of(
            "Windows 95", "Windows 98", "Solaris");

    public static final Set<String> MOBILE_OS_FAMILIES = Set.of(
            "Windows Phone", "Windows Phone OS", "Symbian OS", "Bada",
            "Windows CE", "Windows Mobile", "Maemo");

    public static final Set<String> MOBILE_BROWSER_FAMILIES = Set.of(
            "IE Mobile", "Opera Mobile", "Opera Mini", "Chrome Mobile",
            "Chrome Mobile WebView", "Chrome Mobile iOS");

    public static final Set<String> TABLET_DEVICE_FAMILIES = Set.of(
            "iPad", "BlackBerry Playbook", "Blackberry Playbook", "Kindle",
            "Kindle Fire", "Kindle Fire HD", "Galaxy Tab", "Xoom", "Dell Streak");

    public static final Set<String> TOUCH_CAPABLE_OS_FAMILIES = Set.of(
            "iOS", "Android", "Windows Phone", "Windows CE", "Windows Mobile",
            "Firefox OS", "MeeGo");

    public static final Set<String> TOUCH_CAPABLE_DEVICE_FAMILIES = Set.of(
            "BlackBerry Playbook", "Blackberry Playbook", "Kindle Fire");

    public static final Set<String> EMAIL_PROGRAM_FAMILIES = Set.of(
            "Outlook", "Windows Live Mail", "AirMail", "Apple Mail", "Outlook",
            "Thunderbird", "Lightning", "ThunderBrowse", "Windows Live Mail",
            "The Bat!", "Lotus Notes", "IBM Notes", "Barca", "MailBar", "kmail2",
            "YahooMobileMail");
}
