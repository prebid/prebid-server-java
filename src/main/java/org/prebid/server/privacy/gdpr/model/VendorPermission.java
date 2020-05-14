package org.prebid.server.privacy.gdpr.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class VendorPermission {

    Integer vendorId;

    String bidderName;

    PrivacyEnforcementAction privacyEnforcementAction;
}

