package org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies;

import com.iabtcf.decoder.TCString;
import com.iabtcf.utils.IntIterable;
import org.apache.commons.collections4.SetUtils;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;

public abstract class EnforcePurposeStrategy {

    protected static final Set<PurposeCode> LI_SUPPORTED_PURPOSES = SetUtils.difference(
            Set.of(PurposeCode.values()),
            Set.of(
                    PurposeCode.THREE,
                    PurposeCode.FOUR,
                    PurposeCode.FIVE,
                    PurposeCode.SIX));

    public abstract Stream<VendorPermission> allowedByTypeStrategy(
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

        if (!LI_SUPPORTED_PURPOSES.contains(purpose)) {
            return false;
        }

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

    protected static Stream<VendorPermission> toVendorPermissions(Collection<VendorPermissionWithGvl> permissions) {
        return permissions.stream().map(VendorPermissionWithGvl::getVendorPermission);
    }
}
