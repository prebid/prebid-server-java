package org.prebid.server.privacy.gdpr.tcfstrategies;

import com.iabtcf.decoder.TCString;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.tcfstrategies.typestrategies.BasicEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.typestrategies.NoEnforcePurposeStrategy;
import org.prebid.server.settings.model.EnforcePurpose;
import org.prebid.server.settings.model.Purpose;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class PurposeStrategy {

    private BasicEnforcePurposeStrategy basicEnforcePurposeStrategy;
    private NoEnforcePurposeStrategy noEnforcePurposeStrategy;

    public PurposeStrategy(BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                           NoEnforcePurposeStrategy noEnforcePurposeStrategy) {
        this.basicEnforcePurposeStrategy = basicEnforcePurposeStrategy;
        this.noEnforcePurposeStrategy = noEnforcePurposeStrategy;
    }

    public abstract int getPurposeId();

    public abstract void allow(PrivacyEnforcementAction privacyEnforcementAction);

    public Collection<VendorPermission> processTypePurposeStrategy(TCString vendorConsent,
                                                                   Purpose purpose,
                                                                   Collection<VendorPermission> vendorPermissions) {
        allowedByTypeStrategy(vendorConsent, purpose, vendorPermissions).stream()
                .map(VendorPermission::getPrivacyEnforcementAction)
                .forEach(this::allow);

        return vendorPermissions;
    }

    private Collection<VendorPermission> allowedByTypeStrategy(TCString vendorConsent,
                                                               Purpose purpose,
                                                               Collection<VendorPermission> vendorPermissions) {
        final Collection<VendorPermission> excludedVendors = excludedVendors(vendorPermissions, purpose);
        final Collection<VendorPermission> vendorForPurpose = vendorPermissions.stream()
                .filter(vendorPermission -> !excludedVendors.contains(vendorPermission))
                .collect(Collectors.toList());
        final boolean isEnforceVendors = BooleanUtils.isNotFalse(purpose.getEnforceVendors());

        final EnforcePurpose purposeType = purpose.getEnforcePurpose();
        // Basic by default
        if (purposeType == null || Objects.equals(purposeType, EnforcePurpose.basic)) {
            return allowedByBasicTypeStrategy(vendorConsent, isEnforceVendors, excludedVendors, vendorForPurpose);
        }

        if (Objects.equals(purposeType, EnforcePurpose.no)) {
            return allowedByNoTypeStrategy(vendorConsent, isEnforceVendors, excludedVendors, vendorForPurpose);
        }

        if (Objects.equals(purposeType, EnforcePurpose.full)) {
            return allowedByFullTypeStrategy();
        }

        throw new IllegalArgumentException(
                String.format("Invalid type strategy provided. no/base/full != %s", purposeType));
    }

    protected abstract Collection<VendorPermission> allowedByFullTypeStrategy();

    protected Collection<VendorPermission> allowedByNoTypeStrategy(TCString vendorConsent,
                                                                   boolean isEnforceVendors,
                                                                   Collection<VendorPermission> excludedVendors,
                                                                   Collection<VendorPermission> vendorForPurpose) {
        final Collection<VendorPermission> modifiedVendorPermissions = noEnforcePurposeStrategy
                .allowedByTypeStrategy(getPurposeId(), vendorConsent, vendorForPurpose, isEnforceVendors);

        return CollectionUtils.union(modifiedVendorPermissions, excludedVendors);
    }

    protected Collection<VendorPermission> allowedByBasicTypeStrategy(TCString vendorConsent,
                                                                      boolean isEnforceVendors,
                                                                      Collection<VendorPermission> excludedVendors,
                                                                      Collection<VendorPermission> vendorForPurpose) {
        final Collection<VendorPermission> modifiedVendorPermissions = basicEnforcePurposeStrategy
                .allowedByTypeStrategy(getPurposeId(), vendorConsent, vendorForPurpose, isEnforceVendors);

        return CollectionUtils.union(modifiedVendorPermissions, excludedVendors);
    }

    protected Collection<VendorPermission> excludedVendors(Collection<VendorPermission> vendorPermissions,
                                                           Purpose purpose) {
        final List<String> bidderNameExceptions = purpose.getVendorExceptions();
        return CollectionUtils.isEmpty(bidderNameExceptions)
                ? Collections.emptySet()
                : CollectionUtils.select(vendorPermissions, vendorPermission ->
                bidderNameExceptions.contains(vendorPermission.getBidderName()));
    }
}

