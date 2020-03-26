package org.prebid.server.privacy.gdpr.tcf2stratgies;

import com.iabtcf.decoder.TCString;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.tcf2stratgies.typeStrategies.BasicTypeStrategy;
import org.prebid.server.settings.model.EnforcePurpose;
import org.prebid.server.settings.model.Purpose;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class PurposeStrategy {

    private BasicTypeStrategy basicTypeStrategy;

    public PurposeStrategy(BasicTypeStrategy basicTypeStrategy) {
        this.basicTypeStrategy = basicTypeStrategy;
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
        final EnforcePurpose purposeType = purpose.getEnforcePurpose();
        // Base by default
        if (purposeType == null || Objects.equals(purposeType, EnforcePurpose.base)) {
            return allowedByBasicTypeStrategy(vendorConsent, purpose, vendorPermissions);
        }

        if (Objects.equals(purposeType, EnforcePurpose.no)) {
            return allowedByNoTypeStrategy(vendorPermissions);
        }

        if (Objects.equals(purposeType, EnforcePurpose.full)) {
            return allowedByFullTypeStrategy();
        }

        throw new IllegalArgumentException(
                String.format("Invalid type strategy provided. no/base/full != %s", purposeType));
    }

    protected abstract Collection<VendorPermission> allowedByFullTypeStrategy();

    protected Collection<VendorPermission> allowedByNoTypeStrategy(Collection<VendorPermission> vendorPermissions) {
        return vendorPermissions;
    }

    protected Collection<VendorPermission> allowedByBasicTypeStrategy(TCString vendorConsent,
                                                                      Purpose purpose,
                                                                      Collection<VendorPermission> vendorPermissions) {
        final Collection<VendorPermission> excludedVendors = excludedVendors(vendorPermissions, purpose);
        final Collection<VendorPermission> vendorForPurpose = vendorPermissions.stream()
                .filter(vendorPermission -> !excludedVendors.contains(vendorPermission))
                .collect(Collectors.toList());

        final boolean isEnforceVendors = BooleanUtils.isNotFalse(purpose.getEnforceVendors());
        final Collection<VendorPermission> modifiedVendorPermissions = basicTypeStrategy
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

