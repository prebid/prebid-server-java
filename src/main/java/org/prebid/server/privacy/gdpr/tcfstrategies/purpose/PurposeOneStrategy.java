package org.prebid.server.privacy.gdpr.tcfstrategies.purpose;

import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.BasicEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.FullEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.NoEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Purpose;

public class PurposeOneStrategy extends PurposeStrategy {

    public PurposeOneStrategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                              BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                              NoEnforcePurposeStrategy noEnforcePurposeStrategy) {

        super(fullEnforcePurposeStrategy, basicEnforcePurposeStrategy, noEnforcePurposeStrategy);
    }

    @Override
    public void allow(PrivacyEnforcementAction privacyEnforcementAction) {
        privacyEnforcementAction.setBlockPixelSync(false);
    }

    @Override
    public void allowNaturally(PrivacyEnforcementAction privacyEnforcementAction) {
    }

    @Override
    public Purpose getPurpose() {
        return Purpose.ONE;
    }
}

