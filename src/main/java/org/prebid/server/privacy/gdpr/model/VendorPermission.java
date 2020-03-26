package org.prebid.server.privacy.gdpr.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(exclude = "privacyEnforcementAction")
@AllArgsConstructor(staticName = "of")
@Value
public class VendorPermission {

    Integer vendorId;

    String bidderName;

    PrivacyEnforcementAction privacyEnforcementAction;
}

