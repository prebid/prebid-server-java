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
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;
import org.prebid.server.settings.model.EnforcePurpose;
import org.prebid.server.settings.model.Purpose;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public abstract class PurposeStrategy {

    private final FullEnforcePurposeStrategy fullEnforcePurposeStrategy;
    private final BasicEnforcePurposeStrategy basicEnforcePurposeStrategy;
    private final NoEnforcePurposeStrategy noEnforcePurposeStrategy;

    public PurposeStrategy(FullEnforcePurposeStrategy fullEnforcePurposeStrategy,
                           BasicEnforcePurposeStrategy basicEnforcePurposeStrategy,
                           NoEnforcePurposeStrategy noEnforcePurposeStrategy) {

        this.fullEnforcePurposeStrategy = fullEnforcePurposeStrategy;
        this.basicEnforcePurposeStrategy = basicEnforcePurposeStrategy;
        this.noEnforcePurposeStrategy = noEnforcePurposeStrategy;
    }

    public abstract PurposeCode getPurpose();

    /**
     * This method is allow permission for purpose when account and server config was used.
     */
    protected abstract void allow(PrivacyEnforcementAction privacyEnforcementAction);

    public void allow(VendorPermission vendorPermission) {
        vendorPermission.consentWith(getPurpose());
        allow(vendorPermission.getPrivacyEnforcementAction());
    }

    /**
     * This method represents allowance of permission that purpose should provide after full enforcement
     * (can downgrade to basic if GVL failed) despite of host company or account configuration.
     */
    protected abstract void allowNaturally(PrivacyEnforcementAction privacyEnforcementAction);

    public void allowNaturally(VendorPermission vendorPermission) {
        vendorPermission.consentNaturallyWith(getPurpose());
        allowNaturally(vendorPermission.getPrivacyEnforcementAction());
    }

    public void processTypePurposeStrategy(TCString vendorConsent,
                                           Purpose purpose,
                                           Collection<VendorPermissionWithGvl> vendorPermissions,
                                           boolean wasDowngraded) {

        final Collection<VendorPermissionWithGvl> excludedVendors = excludedVendors(vendorPermissions, purpose);
        final Collection<VendorPermissionWithGvl> vendorForPurpose = vendorPermissions.stream()
                .filter(vendorPermission -> !excludedVendors.contains(vendorPermission))
                .toList();

        allowedByTypeStrategy(vendorConsent, purpose, vendorForPurpose, excludedVendors)
                .forEach(this::allow);

        final Stream<VendorPermission> naturalVendorPermission = wasDowngraded
                ? allowedByBasicTypeStrategy(vendorConsent, true, vendorForPurpose, excludedVendors)
                : allowedByFullTypeStrategy(vendorConsent, true, vendorForPurpose, excludedVendors);

        naturalVendorPermission.forEach(this::allowNaturally);
    }

    private Collection<VendorPermissionWithGvl> excludedVendors(Collection<VendorPermissionWithGvl> vendorPermissions,
                                                                Purpose purpose) {

        final List<String> bidderNameExceptions = purpose.getVendorExceptions();

        return CollectionUtils.isEmpty(bidderNameExceptions)
                ? Collections.emptyList()
                : CollectionUtils.select(vendorPermissions, vendorPermission ->
                bidderNameExceptions.contains(vendorPermission.getVendorPermission().getBidderName()));
    }

    private Stream<VendorPermission> allowedByTypeStrategy(TCString vendorConsent,
                                                           Purpose purpose,
                                                           Collection<VendorPermissionWithGvl> vendorForPurpose,
                                                           Collection<VendorPermissionWithGvl> excludedVendors) {

        final boolean isEnforceVendors = BooleanUtils.isNotFalse(purpose.getEnforceVendors());

        final EnforcePurpose purposeType = purpose.getEnforcePurpose();
        if (purposeType == EnforcePurpose.no) {
            return allowedByNoTypeStrategy(vendorConsent, isEnforceVendors, vendorForPurpose, excludedVendors);
        }

        if (purposeType == EnforcePurpose.basic) {
            return allowedByBasicTypeStrategy(vendorConsent, isEnforceVendors, vendorForPurpose, excludedVendors);
        }

        return allowedByFullTypeStrategy(vendorConsent, isEnforceVendors, vendorForPurpose, excludedVendors);
    }

    private Stream<VendorPermission> allowedByBasicTypeStrategy(
            TCString vendorConsent,
            boolean isEnforceVendors,
            Collection<VendorPermissionWithGvl> vendorForPurpose,
            Collection<VendorPermissionWithGvl> excludedVendors) {

        return basicEnforcePurposeStrategy.allowedByTypeStrategy(
                getPurpose(), vendorConsent, vendorForPurpose, excludedVendors, isEnforceVendors);
    }

    private Stream<VendorPermission> allowedByNoTypeStrategy(
            TCString vendorConsent,
            boolean isEnforceVendors,
            Collection<VendorPermissionWithGvl> vendorForPurpose,
            Collection<VendorPermissionWithGvl> excludedVendors) {

        return noEnforcePurposeStrategy.allowedByTypeStrategy(
                getPurpose(), vendorConsent, vendorForPurpose, excludedVendors, isEnforceVendors);
    }

    private Stream<VendorPermission> allowedByFullTypeStrategy(
            TCString vendorConsent,
            boolean isEnforceVendors,
            Collection<VendorPermissionWithGvl> vendorForPurpose,
            Collection<VendorPermissionWithGvl> excludedVendors) {

        return fullEnforcePurposeStrategy.allowedByTypeStrategy(
                getPurpose(), vendorConsent, vendorForPurpose, excludedVendors, isEnforceVendors);
    }
}

