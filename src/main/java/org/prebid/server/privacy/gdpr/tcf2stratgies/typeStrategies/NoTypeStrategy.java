package org.prebid.server.privacy.gdpr.tcf2stratgies.typeStrategies;

import com.iabtcf.decoder.TCString;
import com.iabtcf.utils.IntIterable;
import org.prebid.server.privacy.gdpr.model.VendorPermission;

import java.util.Collection;
import java.util.stream.Collectors;

public class NoTypeStrategy extends PurposeTypeStrategy {

    public Collection<VendorPermission> allowedByTypeStrategy(
            int purposeId,
            TCString tcString,
            Collection<VendorPermission> vendorsForPurpose,
            boolean isEnforceVendors) {

        final IntIterable vendorConsent = tcString.getVendorConsent();
        final IntIterable vendorLIConsent = tcString.getVendorLegitimateInterest();

        return vendorsForPurpose.stream()
                .filter(vendorPermission -> isAllowedByVendorConsent(vendorPermission.getVendorId(), isEnforceVendors,
                        vendorConsent, vendorLIConsent))
                .collect(Collectors.toList());
    }

    protected boolean isAllowedByVendorConsent(Integer vendorId,
                                               boolean isEnforceVendors,
                                               IntIterable vendorConsent,
                                               IntIterable vendorLIConsent) {
        if (vendorId == null) {
            return false;
        }
        return !isEnforceVendors || vendorConsent.contains(vendorId) || vendorLIConsent.contains(vendorId);
    }
}
