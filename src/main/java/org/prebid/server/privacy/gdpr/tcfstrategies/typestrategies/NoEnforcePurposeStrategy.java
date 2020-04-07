package org.prebid.server.privacy.gdpr.tcfstrategies.typestrategies;

import com.iabtcf.decoder.TCString;
import org.prebid.server.privacy.gdpr.model.VendorPermission;

import java.util.Collection;

public class NoEnforcePurposeStrategy extends EnforcePurposeStrategy {

    public Collection<VendorPermission> allowedByTypeStrategy(
            int purposeId,
            TCString vendorConsent,
            Collection<VendorPermission> vendorsForPurpose,
            boolean isEnforceVendors) {

        return isEnforceVendors
                ? allowedByVendor(vendorConsent, vendorsForPurpose)
                : vendorsForPurpose;
    }
}
