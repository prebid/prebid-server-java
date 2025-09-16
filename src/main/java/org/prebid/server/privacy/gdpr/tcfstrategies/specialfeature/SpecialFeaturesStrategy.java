package org.prebid.server.privacy.gdpr.tcfstrategies.specialfeature;

import com.iabtcf.decoder.TCString;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.settings.model.SpecialFeature;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public abstract class SpecialFeaturesStrategy {

    public abstract int getSpecialFeatureId();

    public abstract void allow(PrivacyEnforcementAction privacyEnforcementAction);

    public void processSpecialFeaturesStrategy(TCString vendorConsent,
                                               SpecialFeature specialFeature,
                                               Collection<VendorPermission> vendorPermissions) {

        if (isOptIn(specialFeature, vendorConsent)) {
            allowFor(vendorPermissions);
        } else {
            allowOnlyExcluded(vendorPermissions, specialFeature);
        }
    }

    private boolean isOptIn(SpecialFeature specialFeature, TCString vendorConsent) {
        return BooleanUtils.isFalse(specialFeature.getEnforce())
                || vendorConsent.getSpecialFeatureOptIns().contains(getSpecialFeatureId());
    }

    private void allowFor(Collection<VendorPermission> vendorPermissions) {
        vendorPermissions.forEach(vendorPermission -> allow(vendorPermission.getPrivacyEnforcementAction()));
    }

    private void allowOnlyExcluded(Collection<VendorPermission> vendorPermissions, SpecialFeature specialFeature) {
        excludedVendors(vendorPermissions, specialFeature)
                .map(VendorPermission::getPrivacyEnforcementAction)
                .forEach(this::allow);
    }

    private Stream<VendorPermission> excludedVendors(Collection<VendorPermission> vendorPermissions,
                                                     SpecialFeature specialFeature) {

        final List<String> bidderNameExceptions = specialFeature.getVendorExceptions();

        return CollectionUtils.isEmpty(bidderNameExceptions)
                ? Stream.empty()
                : vendorPermissions.stream()
                .filter(vendorPermission -> bidderNameExceptions.contains(vendorPermission.getBidderName()));
    }
}
