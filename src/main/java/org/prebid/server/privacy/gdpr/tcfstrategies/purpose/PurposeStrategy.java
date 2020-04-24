package org.prebid.server.privacy.gdpr.tcfstrategies.purpose;

import com.iabtcf.decoder.TCString;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.BasicEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.FullEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.NoEnforcePurposeStrategy;
import org.prebid.server.settings.model.EnforcePurpose;
import org.prebid.server.settings.model.Purpose;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class PurposeStrategy {

    private FullEnforcePurposeStrategy fullEnforcePurposeStrategy;
    private BasicEnforcePurposeStrategy basicEnforcePurposeStrategy;
    private NoEnforcePurposeStrategy noEnforcePurposeStrategy;

    public PurposeStrategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                           BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                           NoEnforcePurposeStrategy noEnforcePurposeStrategy) {
        this.fullEnforcePurposeStrategy = fullEnforcePurposeStrategy;
        this.basicEnforcePurposeStrategy = basicEnforcePurposeStrategy;
        this.noEnforcePurposeStrategy = noEnforcePurposeStrategy;
    }

    public abstract int getPurposeId();

    public abstract void allow(PrivacyEnforcementAction privacyEnforcementAction);

    public Collection<VendorPermission> processTypePurposeStrategy(
            TCString vendorConsent, Purpose purpose, Collection<VendorPermissionWithGvl> vendorPermissions) {

        allowedByTypeStrategy(vendorConsent, purpose, vendorPermissions).stream()
                .map(VendorPermission::getPrivacyEnforcementAction)
                .forEach(this::allow);

        return vendorPermissions.stream()
                .map(VendorPermissionWithGvl::getVendorPermission)
                .collect(Collectors.toList());
    }

    private Collection<VendorPermission> allowedByTypeStrategy(TCString vendorConsent,
                                                               Purpose purpose,
                                                               Collection<VendorPermissionWithGvl> vendorPermissions) {
        final Collection<VendorPermissionWithGvl> excludedVendors = excludedVendors(vendorPermissions, purpose);
        final Collection<VendorPermissionWithGvl> vendorForPurpose = vendorPermissions.stream()
                .filter(vendorPermission -> !excludedVendors.contains(vendorPermission))
                .collect(Collectors.toList());
        final boolean isEnforceVendors = BooleanUtils.isNotFalse(purpose.getEnforceVendors());

        final EnforcePurpose purposeType = purpose.getEnforcePurpose();
        if (Objects.equals(purposeType, EnforcePurpose.basic)) {
            return allowedByBasicTypeStrategy(vendorConsent, isEnforceVendors, vendorForPurpose, excludedVendors);
        }

        if (Objects.equals(purposeType, EnforcePurpose.no)) {
            return allowedByNoTypeStrategy(vendorConsent, isEnforceVendors, vendorForPurpose, excludedVendors);
        }

        // Full by default
        if (purposeType == null || Objects.equals(purposeType, EnforcePurpose.full)) {
            return allowedByFullTypeStrategy(vendorConsent, isEnforceVendors, vendorForPurpose, excludedVendors);
        }

        throw new IllegalArgumentException(
                String.format("Invalid type strategy provided. no/base/full != %s", purposeType));
    }

    protected Collection<VendorPermissionWithGvl> excludedVendors(Collection<VendorPermissionWithGvl> vendorPermissions,
                                                                  Purpose purpose) {
        final List<String> bidderNameExceptions = purpose.getVendorExceptions();
        return CollectionUtils.isEmpty(bidderNameExceptions)
                ? Collections.emptyList()
                : CollectionUtils.select(vendorPermissions, vendorPermission ->
                bidderNameExceptions.contains(vendorPermission.getVendorPermission().getBidderName()));
    }

    protected Collection<VendorPermission> allowedByBasicTypeStrategy(
            TCString vendorConsent, boolean isEnforceVendors, Collection<VendorPermissionWithGvl> vendorForPurpose,
            Collection<VendorPermissionWithGvl> excludedVendors) {

        return basicEnforcePurposeStrategy.allowedByTypeStrategy(getPurposeId(), vendorConsent, vendorForPurpose,
                excludedVendors, isEnforceVendors);
    }

    protected Collection<VendorPermission> allowedByNoTypeStrategy(
            TCString vendorConsent, boolean isEnforceVendors, Collection<VendorPermissionWithGvl> vendorForPurpose,
            Collection<VendorPermissionWithGvl> excludedVendors) {

        return noEnforcePurposeStrategy.allowedByTypeStrategy(getPurposeId(), vendorConsent, vendorForPurpose,
                excludedVendors, isEnforceVendors);
    }

    protected Collection<VendorPermission> allowedByFullTypeStrategy(
            TCString vendorConsent, boolean isEnforceVendors, Collection<VendorPermissionWithGvl> vendorForPurpose,
            Collection<VendorPermissionWithGvl> excludedVendors) {

        return fullEnforcePurposeStrategy.allowedByTypeStrategy(getPurposeId(), vendorConsent, excludedVendors,
                vendorForPurpose, isEnforceVendors);

    }
}

