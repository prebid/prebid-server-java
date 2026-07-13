package org.prebid.server.privacy.gdpr.tcfstrategies.specialfeature;

import org.prebid.server.privacy.gdpr.DisclosedVendorsStrictness;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;

public class SpecialFeaturesOneStrategy extends SpecialFeaturesStrategy {

    public SpecialFeaturesOneStrategy(DisclosedVendorsStrictness disclosedVendorsStrictness) {
        super(disclosedVendorsStrictness);
    }

    @Override
    public int getSpecialFeatureId() {
        return 1;
    }

    @Override
    public void allow(PrivacyEnforcementAction privacyEnforcementAction) {
        privacyEnforcementAction.setMaskDeviceIp(false);
        privacyEnforcementAction.setMaskGeo(false);
    }
}
