package org.prebid.server.privacy.gdpr.tcfstrategies.specialfeature;

import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;

public class SpecialFeaturesOneStrategy extends SpecialFeaturesStrategy {

    @Override
    public int getSpecialFeatureId() {
        return 1;
    }

    @Override
    public void allow(PrivacyEnforcementAction privacyEnforcementAction) {
        privacyEnforcementAction.setMaskDeviceIp(true);
        privacyEnforcementAction.setMaskGeo(true);
    }
}
