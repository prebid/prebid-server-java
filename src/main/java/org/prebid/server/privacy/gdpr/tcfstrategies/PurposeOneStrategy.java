package org.prebid.server.privacy.gdpr.tcfstrategies;

import org.apache.commons.lang3.NotImplementedException;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.tcfstrategies.typestrategies.BasicEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.typestrategies.NoEnforcePurposeStrategy;

import java.util.Collection;

public class PurposeOneStrategy extends PurposeStrategy {

    private static final Integer PURPOSE_ID = 1;

    public PurposeOneStrategy(BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                              NoEnforcePurposeStrategy noEnforcePurposeStrategy) {
        super(basicEnforcePurposeStrategy, noEnforcePurposeStrategy);
    }

    @Override
    public void allow(PrivacyEnforcementAction privacyEnforcementAction) {
        privacyEnforcementAction.setBlockPixelSync(false);
    }

    @Override
    public int getPurposeId() {
        return PURPOSE_ID;
    }

    @Override
    protected Collection<VendorPermission> allowedByFullTypeStrategy() {
        throw new NotImplementedException("Full not implemented for TCF 2.0 For Purpose 1");
    }
}

