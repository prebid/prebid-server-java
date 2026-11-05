package org.prebid.server.hooks.modules.intentiq.identity.v1.core;

import org.apache.commons.lang3.StringUtils;
import ua_parser.Client;
import ua_parser.Parser;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Normalizes a raw User-Agent string into a compact, deterministic cache-key segment.
 *
 * <p>The IntentIQ caching guidance asks integrators to key on <em>normalized</em> UA fields
 * (OS family/version, browser family/major, device family) rather than the full raw UA string,
 * which would fragment the cache. The resulting segment looks like {@code iOS17_MobileSafari17_iPhone}.
 *
 * <p>Parsing is deterministic, so the same device always yields the same segment — which is all the
 * module's self-contained cache requires. (Rendering engine and app-vs-browser, shown in some guide
 * samples, are not exposed by this parser and are intentionally omitted.)
 */
public final class DeviceUserAgent {

    private static final Parser UA_PARSER = new Parser();
    private static final String UNKNOWN = "Other";

    private DeviceUserAgent() {
    }

    /**
     * Returns the normalized UA segment, or an empty string when {@code ua} is blank or yields no
     * recognizable fields.
     */
    public static String normalize(String ua) {
        if (StringUtils.isBlank(ua)) {
            return StringUtils.EMPTY;
        }

        final Client client = UA_PARSER.parse(ua);
        final String os = client.os != null ? token(client.os.family, client.os.major) : null;
        final String browser = client.userAgent != null
                ? token(client.userAgent.family, client.userAgent.major)
                : null;
        final String device = client.device != null ? token(client.device.family, null) : null;

        return Stream.of(os, browser, device)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("_"));
    }

    private static String token(String family, String version) {
        if (StringUtils.isBlank(family) || UNKNOWN.equals(family)) {
            return null;
        }
        final String value = family + StringUtils.defaultString(version);
        return Optional.of(value.replaceAll("\\s+", "")).filter(StringUtils::isNotBlank).orElse(null);
    }
}
