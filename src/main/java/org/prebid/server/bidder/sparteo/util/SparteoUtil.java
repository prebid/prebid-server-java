package org.prebid.server.bidder.sparteo.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.net.URI;
import java.net.URISyntaxException;

public final class SparteoUtil {

    private SparteoUtil() {
    }

    public static String normalizeHostname(String host) {
        String h = StringUtils.trimToEmpty(host);
        if (h.isEmpty()) {
            return "";
        }

        String hostname = null;
        try {
            hostname = new URI(h).getHost();
        } catch (URISyntaxException e) {
        }

        if (StringUtils.isNotEmpty(hostname)) {
            h = hostname;
        } else {
            if (h.contains(":")) {
                h = StringUtils.substringBefore(h, ":");
            } else {
                h = StringUtils.substringBefore(h, "/");
            }
        }

        h = h.toLowerCase();
        h = Strings.CS.removeStart(h, "www.");
        h = Strings.CS.removeEnd(h, ".");

        return "null".equals(h) ? "" : h;
    }
}
