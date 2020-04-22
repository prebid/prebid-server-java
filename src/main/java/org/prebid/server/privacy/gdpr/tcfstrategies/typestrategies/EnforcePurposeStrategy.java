package org.prebid.server.privacy.gdpr.tcfstrategies.typestrategies;

import com.iabtcf.decoder.TCString;
import com.iabtcf.utils.IntIterable;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;

import java.util.Collection;
import java.util.stream.Collectors;

public abstract class EnforcePurposeStrategy {

    public abstract Collection<VendorPermission> allowedByTypeStrategy(
            int purposeId, TCString vendorConsent, Collection<VendorPermissionWithGvl> vendorsForPurpose,
            Collection<VendorPermissionWithGvl> excludedVendors, boolean isEnforceVendors);

    protected boolean isAllowedBySimpleConsentOrLegitimateInterest(int purposeId,
                                                                   Integer vendorId,
                                                                   boolean isEnforceVendor,
                                                                   TCString tcString) {
        return isAllowedBySimpleConsent(purposeId, vendorId, isEnforceVendor, tcString)
                || isAllowedByLegitimateInterest(purposeId, vendorId, isEnforceVendor, tcString);

    }

    protected boolean isAllowedBySimpleConsent(int purposeId,
                                               Integer vendorId,
                                               boolean isEnforceVendor,
                                               TCString tcString) {
        final IntIterable purposesConsent = tcString.getPurposesConsent();
        final IntIterable vendorConsent = tcString.getVendorConsent();
        return isAllowedByConsents(purposeId, vendorId, isEnforceVendor, purposesConsent, vendorConsent);
    }

    protected boolean isAllowedByLegitimateInterest(int purposeId,
                                                    Integer vendorId,
                                                    boolean isEnforceVendor,
                                                    TCString tcString) {
        final IntIterable purposesConsent = tcString.getPurposesLITransparency();
        final IntIterable vendorConsent = tcString.getVendorLegitimateInterest();
        return isAllowedByConsents(purposeId, vendorId, isEnforceVendor, purposesConsent, vendorConsent);
    }

    private boolean isAllowedByConsents(int purposeId,
                                        Integer vendorId,
                                        boolean isEnforceVendors,
                                        IntIterable purposesConsent,
                                        IntIterable vendorConsent) {
        final boolean isPurposeAllowed = purposesConsent.contains(purposeId);
        final boolean isVendorAllowed = !isEnforceVendors || vendorConsent.contains(vendorId);
        return isPurposeAllowed && isVendorAllowed;
    }

    protected static Collection<VendorPermission> toVendorPermissions(
            Collection<VendorPermissionWithGvl> vendorPermissionWithGvls) {

        return vendorPermissionWithGvls.stream()
                .map(VendorPermissionWithGvl::getVendorPermission)
                .collect(Collectors.toList());
    }
}
