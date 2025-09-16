package org.prebid.server.privacy.gdpr.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class HostVendorTcfResponse {

    private static final HostVendorTcfResponse ALLOWED_VENDOR = HostVendorTcfResponse.of(null, null, true);

    Boolean userInGdprScope;

    String country;

    boolean isVendorAllowed;

    public static HostVendorTcfResponse allowedVendor() {
        return ALLOWED_VENDOR;
    }
}
