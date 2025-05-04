package org.prebid.server.privacy.gdpr.model;

import lombok.Value;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;

import java.util.EnumSet;
import java.util.Set;

@Value(staticConstructor = "of")
public class VendorPermission {

    Integer vendorId;

    String bidderName;

    Set<PurposeCode> consentedPurposes = EnumSet.noneOf(PurposeCode.class);

    Set<PurposeCode> naturallyConsentedPurposes = EnumSet.noneOf(PurposeCode.class);

    PrivacyEnforcementAction privacyEnforcementAction;

    public void consentWith(PurposeCode purposeCode) {
        consentedPurposes.add(purposeCode);
    }

    public void consentNaturallyWith(PurposeCode purposeCode) {
        naturallyConsentedPurposes.add(purposeCode);
    }
}
