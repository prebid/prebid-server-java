package org.prebid.server.privacy.gdpr.tcfstrategies.purpose;

import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.BasicEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.FullEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.NoEnforcePurposeStrategy;

public class PurposeFiveStrategy extends PurposeStrategy {

    private static final int PURPOSE_ID = 5;

    public PurposeFiveStrategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
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
    public int getPurposeId() {
        return PURPOSE_ID;
    }
}

