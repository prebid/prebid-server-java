package org.prebid.server.privacy.gdpr.model;

import lombok.AllArgsConstructor;
import lombok.Value;

// Can't be used in hash related CCollections
//@EqualsAndHashCode(exclude = "privacyEnforcementAction")
@AllArgsConstructor(staticName = "of")
@Value
public class VendorPermission {

    Integer vendorId;

    String bidderName;

    PrivacyEnforcementAction privacyEnforcementAction;
}

