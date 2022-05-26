package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

import java.util.List;

/**
 * Further identification based on <a href="https://wicg.github.io/ua-client-hints/">
 * User-Agent Client Hints</a> , the {@link BrandVersion} object is used to identify
 * a device’s browser or similar software component, and the user agent’s execution
 * platform or operating system.
 */
@Value
public class BrandVersion {

    /**
     * A brand identifier, for example, “Chrome” or “Windows”. The value may be
     * sourced from the User-Agent Client Hints headers, representing either the
     * user agent brand (from the Sec-CH-UA-Full-Version header) or the platform
     * brand (from the Sec-CH-UA-Platform header).
     * <p/>(required)
     */
    String brand;

    /**
     * A sequence of version components, in descending hierarchical order (major, minor, micro, ...)
     */
    List<String> version;

    /**
     * Placeholder for vendor specific extensions to this object
     */
    ObjectNode ext;
}
