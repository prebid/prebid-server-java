package org.prebid.server.privacy.gdpr.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;

import java.util.EnumSet;
import java.util.Set;

@Value(staticConstructor = "of")
public class VendorPermission {

    Integer vendorId;

    String bidderName;

    @Getter(AccessLevel.NONE)
    Set<PurposeCode> consentedPurposes = EnumSet.noneOf(PurposeCode.class);

    PrivacyEnforcementAction privacyEnforcementAction;

    public void consent(PurposeCode purposeCode) {
        consentedPurposes.add(purposeCode);
    }

    public boolean isConsented(PurposeCode purposeCode) {
        return consentedPurposes.contains(purposeCode);
    }
}

