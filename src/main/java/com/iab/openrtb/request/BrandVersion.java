package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

import java.util.List;

@Value
public class BrandVersion {

    /**
     * A brand identifier, for example, “Chrome” or “Windows”. The value may be
     * sourced from the User-Agent Client Hints headers, representing either the
     * user agent brand (from the Sec-CH-UA-Full-Version header) or the platform
     * brand (from the Sec-CH-UA-Platform header).
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
