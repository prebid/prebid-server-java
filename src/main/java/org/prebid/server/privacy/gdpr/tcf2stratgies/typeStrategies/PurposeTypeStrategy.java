package org.prebid.server.privacy.gdpr.tcf2stratgies.typeStrategies;

import com.iabtcf.decoder.TCString;
import com.iabtcf.utils.IntIterable;
import org.prebid.server.privacy.gdpr.model.VendorPermission;

import java.util.Collection;

public abstract class PurposeTypeStrategy {

    public abstract Collection<VendorPermission> allowedByTypeStrategy(
            int purposeId,
            TCString vendorConsent,
            Collection<VendorPermission> vendorsForPurpose,
            boolean isEnforceVendors);

    protected boolean isAllowedByConsents(int purposeId,
                                          Integer vendorId,
                                          boolean isEnforceVendors,
                                          IntIterable purposesConsent,
                                          IntIterable vendorConsent) {
        if (vendorId == null) {
            return false;
        }
        final boolean isPurposeAllowed = purposesConsent.contains(purposeId);
        final boolean isVendorAllowed = isEnforceVendors ? vendorConsent.contains(vendorId) : true;
        return isPurposeAllowed && isVendorAllowed;
    }
}
