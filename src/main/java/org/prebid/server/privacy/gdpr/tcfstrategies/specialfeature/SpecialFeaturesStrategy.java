package org.prebid.server.privacy.gdpr.tcfstrategies.specialfeature;

import com.iabtcf.decoder.TCString;
import com.iabtcf.utils.IntIterable;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.settings.model.SpecialFeature;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class SpecialFeaturesStrategy {

    public abstract int getSpecialFeatureId();

    public abstract void allow(PrivacyEnforcementAction privacyEnforcementAction);

    public Collection<VendorPermission> processSpecialFeaturesStrategy(TCString vendorConsent,
                                                                       SpecialFeature specialFeature,
                                                                       Collection<VendorPermission> vendorPermissions) {
        // Default True
        if (BooleanUtils.isFalse(specialFeature.getEnforce())) {
            return allowFor(vendorPermissions);
        }

        final IntIterable specialFeatureOptIns = vendorConsent.getSpecialFeatureOptIns();
        final boolean isSpecialFeatureIsOptIn = specialFeatureOptIns.contains(getSpecialFeatureId());

        return isSpecialFeatureIsOptIn
                ? allowFor(vendorPermissions)
                : allowOnlyExcluded(vendorPermissions, specialFeature);
    }

    private Collection<VendorPermission> allowFor(Collection<VendorPermission> vendorPermissions) {
        vendorPermissions.forEach(vendorPermission -> allow(vendorPermission.getPrivacyEnforcementAction()));
        return vendorPermissions;
    }

    private Collection<VendorPermission> allowOnlyExcluded(Collection<VendorPermission> vendorPermissions,
                                                           SpecialFeature specialFeature) {
        excludedVendors(vendorPermissions, specialFeature)
                .forEach(vendorPermission -> allow(vendorPermission.getPrivacyEnforcementAction()));
        return vendorPermissions;
    }

    protected Collection<VendorPermission> excludedVendors(Collection<VendorPermission> vendorPermissions,
                                                           SpecialFeature specialFeature) {
        final List<String> bidderNameExceptions = specialFeature.getVendorExceptions();
        return CollectionUtils.isEmpty(bidderNameExceptions)
                ? Collections.emptyList()
                : CollectionUtils.select(vendorPermissions, vendorPermission ->
                        bidderNameExceptions.contains(vendorPermission.getBidderName()));
    }
}
