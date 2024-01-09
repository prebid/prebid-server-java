package org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies;

import com.iabtcf.decoder.TCString;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;

public class PurposeTwoBasicEnforcePurposeStrategy extends BasicEnforcePurposeStrategy {

    @Override
    protected boolean isAllowedBySimpleConsent(PurposeCode purpose,
                                               Integer vendorId,
                                               boolean isEnforceVendor,
                                               TCString tcString) {

        return super.isAllowedBySimpleConsent(purpose, vendorId, isEnforceVendor, tcString)
                || tcString.getPurposesLITransparency().contains(purpose.code());
    }
}
