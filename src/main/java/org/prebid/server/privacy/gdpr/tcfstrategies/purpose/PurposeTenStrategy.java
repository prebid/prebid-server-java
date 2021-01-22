package org.prebid.server.privacy.gdpr.tcfstrategies.purpose;

import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.BasicEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.FullEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.NoEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Purpose;

public class PurposeTenStrategy extends PurposeStrategy {

    public PurposeTenStrategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                              BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                              NoEnforcePurposeStrategy noEnforcePurposeStrategy) {

        super(fullEnforcePurposeStrategy, basicEnforcePurposeStrategy, noEnforcePurposeStrategy);
    }

    @Override
    public void allow(PrivacyEnforcementAction privacyEnforcementAction) {
    }

    @Override
    public void allowNaturally(PrivacyEnforcementAction privacyEnforcementAction) {
        privacyEnforcementAction.setRemoveUserIds(false);
        privacyEnforcementAction.setMaskDeviceInfo(false);
    }

    @Override
    public Purpose getPurpose() {
        return Purpose.TEN;
    }
}

