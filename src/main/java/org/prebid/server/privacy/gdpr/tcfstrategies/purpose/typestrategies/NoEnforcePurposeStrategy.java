package org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies;

import com.iabtcf.decoder.TCString;
import com.iabtcf.utils.IntIterable;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;

import java.util.Collection;
import java.util.stream.Stream;

public class NoEnforcePurposeStrategy extends EnforcePurposeStrategy {

    public Stream<VendorPermission> allowedByTypeStrategy(PurposeCode purpose,
                                                          TCString tcString,
                                                          Collection<VendorPermissionWithGvl> vendorsForPurpose,
                                                          Collection<VendorPermissionWithGvl> excludedVendors,
                                                          boolean isEnforceVendors) {

        final IntIterable vendorConsent = tcString.getVendorConsent();
        final IntIterable vendorLIConsent = tcString.getVendorLegitimateInterest();

        final Stream<VendorPermission> allowedVendorPermissions = toVendorPermissions(vendorsForPurpose)
                .filter(vendorPermission -> vendorPermission.getVendorId() != null)
                .filter(vendorPermission -> isAllowedByVendorConsent(
                        vendorPermission.getVendorId(), isEnforceVendors, vendorConsent, vendorLIConsent));

        return Stream.concat(allowedVendorPermissions, toVendorPermissions(excludedVendors));
    }

    private boolean isAllowedByVendorConsent(Integer vendorId,
                                             boolean isEnforceVendors,
                                             IntIterable vendorConsent,
                                             IntIterable vendorLIConsent) {

        return !isEnforceVendors || vendorConsent.contains(vendorId) || vendorLIConsent.contains(vendorId);
    }
}
