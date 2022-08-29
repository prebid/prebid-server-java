package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Structured user agent information, which can be used when a client supports
 * <a href="https://wicg.github.io/ua-client-hints/">User-Agent Client Hints</a>.
 * If both device.ua and device.sua are present in the bid request, device.sua
 * should be considered the more accurate representation of the device attributes.
 * This is because the device.ua may contain a frozen or reduced user agent string
 * due to deprecation of user agent strings by browsers.
 */
@Builder
@Value
public class UserAgent {

    /**
     * Each {@link BrandVersion} object identifies a browser or similar
     * software component. Implementers should send brands and versions
     * derived from the Sec-CH-UA-Full-Version-List header or an equivalent
     * JavaScript accessor from NavigatorUAData interface. This header or
     * accessor are only available for browsers that support User-Agent Client Hints.
     */
    List<BrandVersion> browsers;

    /**
     * A {@link BrandVersion} object that identifies the user agent’s
     * execution platform / OS. Implementers should send a brand derived from the
     * Sec-CH-UA-Platform header, and version derived from the Sec-CH-UA-
     * Platform-Version header  or an equivalent JavaScript accessor from
     * NavigatorUAData interface. This header or accessor are only available
     * for browsers that support User-Agent Client Hints.
     */
    BrandVersion platform;

    /**
     * 1 if the agent prefers a “mobile” version of the content, if available, i.e.
     * optimized for small screens or touch input. 0 if the agent prefers the “desktop”
     * or “full” content. Implementers should derive this value from the Sec-CH-UA-
     * Mobile header or an equivalent JavaScript accessor from NavigatorUAData interface.
     * This header or accessor are only available for browsers that support User-Agent Client Hints.
     */
    Integer mobile;

    /**
     * Device’s major binary architecture, e.g. “x86” or “arm”. Implementers should
     * retrieve this value from the Sec-CH-UA-Arch header or an equivalent JavaScript
     * accessor from NavigatorUAData interface. This header or accessor are only
     * available for browsers that support User-Agent Client Hints.
     */
    String architecture;

    /**
     * Device’s bitness, e.g. “64” for 64-bit architecture. Implementers should
     * retrieve this value from the Sec-CH-UA-Bitness header or an equivalent
     * JavaScript accessor from NavigatorUAData interface. This header or accessor
     * are only available for browsers that support User-Agent Client Hints.
     */
    String bitness;

    /**
     * Device model. Implementers should retrieve this value from the Sec-CH-UA-
     * Model header or an equivalent JavaScript accessor from NavigatorUAData
     * interface. This header or accessor are only available for browsers that
     * support User-Agent Client Hints.
     */
    String model;

    /**
     * The source of data used to create this object, <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--user-agent-source-">List: User-Agent Source</a> in
     * AdCOM 1.0
     */
    Integer source;

    /**
     * Placeholder for vendor specific extensions to this object
     */
    ObjectNode ext;
}
