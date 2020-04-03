package org.prebid.server.privacy.gdpr.tcfstrategies;

import org.apache.commons.lang3.NotImplementedException;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.tcfstrategies.typestrategies.BasicEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.typestrategies.NoEnforcePurposeStrategy;

import java.util.Collection;

public class PurposeTwoStrategy extends PurposeStrategy {

    private static final int PURPOSE_ID = 2;

    public PurposeTwoStrategy(BasicEnforcePurposeStrategy basicTypeStrategy, NoEnforcePurposeStrategy noTypeStrategy) {
        super(basicTypeStrategy, noTypeStrategy);
    }

    @Override
    public void allow(PrivacyEnforcementAction privacyEnforcementAction) {
        privacyEnforcementAction.setBlockBidderRequest(false);
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

