package org.prebid.server.privacy.gdpr.tcfstrategies.specialfeature;

import com.iabtcf.decoder.TCString;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.privacy.gdpr.DisclosedVendorsStrictness;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.settings.model.SpecialFeature;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public abstract class SpecialFeaturesStrategy {

    private final DisclosedVendorsStrictness disclosedVendorsStrictness;

    protected SpecialFeaturesStrategy(DisclosedVendorsStrictness disclosedVendorsStrictness) {
        this.disclosedVendorsStrictness = Objects.requireNonNull(disclosedVendorsStrictness);
    }

    public abstract int getSpecialFeatureId();

    public abstract void allow(PrivacyEnforcementAction privacyEnforcementAction);

    public void processSpecialFeaturesStrategy(TCString vendorConsent,
                                               SpecialFeature specialFeature,
                                               Collection<VendorPermission> vendorPermissions) {

        if (isOptIn(specialFeature, vendorConsent)) {
            allowFor(disclosedVendors(vendorConsent, vendorPermissions));
        }
        allowFor(excludedVendors(specialFeature, vendorPermissions));
    }

    private boolean isOptIn(SpecialFeature specialFeature, TCString vendorConsent) {
        return BooleanUtils.isFalse(specialFeature.getEnforce())
                || vendorConsent.getSpecialFeatureOptIns().contains(getSpecialFeatureId());
    }

    private Stream<VendorPermission> disclosedVendors(TCString vendorConsent,
                                                      Collection<VendorPermission> vendorPermissions) {

        return vendorPermissions.stream()
                .filter(vendorPermission -> disclosedVendorsStrictness
                        .isVendorDisclosed(vendorConsent, vendorPermission.getVendorId()));
    }

    private void allowFor(Stream<VendorPermission> vendorPermissions) {
        vendorPermissions.forEach(vendorPermission -> allow(vendorPermission.getPrivacyEnforcementAction()));
    }

    private Stream<VendorPermission> excludedVendors(SpecialFeature specialFeature,
                                                     Collection<VendorPermission> vendorPermissions) {

        final List<String> bidderNameExceptions = specialFeature.getVendorExceptions();

        return CollectionUtils.isEmpty(bidderNameExceptions)
                ? Stream.empty()
                : vendorPermissions.stream()
                  .filter(vendorPermission -> bidderNameExceptions.contains(vendorPermission.getBidderName()));
    }
}
