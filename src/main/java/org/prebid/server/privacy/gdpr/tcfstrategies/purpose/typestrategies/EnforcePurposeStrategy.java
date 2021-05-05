package org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies;

import com.iabtcf.decoder.TCString;
import com.iabtcf.utils.IntIterable;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;

import java.util.Collection;
import java.util.stream.Collectors;

public abstract class EnforcePurposeStrategy {

    public abstract Collection<VendorPermission> allowedByTypeStrategy(
            PurposeCode purpose,
            TCString vendorConsent,
            Collection<VendorPermissionWithGvl> vendorsForPurpose,
            Collection<VendorPermissionWithGvl> excludedVendors,
            boolean isEnforceVendors);

    protected boolean isAllowedBySimpleConsentOrLegitimateInterest(PurposeCode purpose,
                                                                   Integer vendorId,
                                                                   boolean isEnforceVendor,
                                                                   TCString tcString) {

        return isAllowedBySimpleConsent(purpose, vendorId, isEnforceVendor, tcString)
                || isAllowedByLegitimateInterest(purpose, vendorId, isEnforceVendor, tcString);

    }

    protected boolean isAllowedBySimpleConsent(PurposeCode purpose,
                                               Integer vendorId,
                                               boolean isEnforceVendor,
                                               TCString tcString) {

        final IntIterable purposesConsent = tcString.getPurposesConsent();
        final IntIterable vendorConsent = tcString.getVendorConsent();

        return isAllowedByConsents(purpose, vendorId, isEnforceVendor, purposesConsent, vendorConsent);
    }

    protected boolean isAllowedByLegitimateInterest(PurposeCode purpose,
                                                    Integer vendorId,
                                                    boolean isEnforceVendor,
                                                    TCString tcString) {

        final IntIterable purposesConsent = tcString.getPurposesLITransparency();
        final IntIterable vendorConsent = tcString.getVendorLegitimateInterest();

        return isAllowedByConsents(purpose, vendorId, isEnforceVendor, purposesConsent, vendorConsent);
    }

    private boolean isAllowedByConsents(PurposeCode purpose,
                                        Integer vendorId,
                                        boolean isEnforceVendors,
                                        IntIterable purposesConsent,
                                        IntIterable vendorConsent) {

        final boolean isPurposeAllowed = purposesConsent.contains(purpose.code());
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
