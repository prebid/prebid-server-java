package org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies;

import com.iabtcf.decoder.TCString;
import com.iabtcf.v2.PublisherRestriction;
import com.iabtcf.v2.RestrictionType;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Purpose;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorV2;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class FullEnforcePurposeStrategy extends EnforcePurposeStrategy {

    public Collection<VendorPermission> allowedByTypeStrategy(Purpose purpose,
                                                              TCString vendorConsent,
                                                              Collection<VendorPermissionWithGvl> vendorsForPurpose,
                                                              Collection<VendorPermissionWithGvl> excludedVendors,
                                                              boolean isEnforceVendors) {

        final List<PublisherRestriction> publisherRestrictions = vendorConsent.getPublisherRestrictions().stream()
                .filter(publisherRestriction -> publisherRestriction.getPurposeId() == purpose.code())
                .collect(Collectors.toList());

        final List<VendorPermission> allowedExcluded = allowedExcludedVendorPermission(excludedVendors,
                publisherRestrictions);

        final Map<VendorPermissionWithGvl, RestrictionType> vendorPermissionToRestriction = mapVendorPermission(
                vendorsForPurpose, publisherRestrictions);

        final List<VendorPermission> allowedVendorPermissions = vendorPermissionToRestriction.entrySet().stream()
                .filter(permissionAndRestriction ->
                        isAllowedByPublisherRestrictionAndFlexible(purpose, isEnforceVendors,
                                permissionAndRestriction.getKey(), vendorConsent, permissionAndRestriction.getValue()))
                .map(Map.Entry::getKey)
                .map(VendorPermissionWithGvl::getVendorPermission)
                .collect(Collectors.toList());

        return CollectionUtils.union(allowedExcluded, allowedVendorPermissions);
    }

    private List<VendorPermission> allowedExcludedVendorPermission(
            Collection<VendorPermissionWithGvl> excludedVendors,
            Collection<PublisherRestriction> publisherRestrictions) {

        final List<Integer> notAllowedVendorIds = publisherRestrictions.stream()
                .filter(publisherRestriction -> publisherRestriction.getRestrictionType()
                        .equals(RestrictionType.NOT_ALLOWED))
                .map(PublisherRestriction::getVendorIds)
                .flatMap(vendorIds -> StreamSupport.stream(vendorIds.spliterator(), false))
                .collect(Collectors.toList());

        return excludedVendors.stream()
                .map(VendorPermissionWithGvl::getVendorPermission)
                .filter(vendorPermissionWithGvl -> isNotRestricted(notAllowedVendorIds, vendorPermissionWithGvl))
                .collect(Collectors.toList());
    }

    private boolean isNotRestricted(List<Integer> notAllowedVendorIds, VendorPermission vendorPermission) {
        final Integer vendorId = vendorPermission.getVendorId();
        return vendorId == null || !notAllowedVendorIds.contains(vendorId);
    }

    private Map<VendorPermissionWithGvl, RestrictionType> mapVendorPermission(
            Collection<VendorPermissionWithGvl> vendorsForPurpose,
            Collection<PublisherRestriction> publisherRestrictions) {

        return vendorsForPurpose.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        vendorPermissionWithGvl -> restrictionType(vendorPermissionWithGvl, publisherRestrictions)));
    }

    private RestrictionType restrictionType(VendorPermissionWithGvl vendorPermissionWithGvl,
                                            Collection<PublisherRestriction> publisherRestrictions) {

        final VendorPermission vendorPermission = vendorPermissionWithGvl.getVendorPermission();
        final Integer vendorId = vendorPermission.getVendorId();

        return publisherRestrictions.stream()
                .filter(publisherRestriction -> publisherRestriction.getVendorIds().contains(vendorId))
                .map(PublisherRestriction::getRestrictionType)
                .findFirst()
                .orElse(RestrictionType.UNDEFINED);
    }

    /**
     * Purpose is flexible when {@link VendorV2} flexiblePurposes contains it.
     * When it is not flexible:
     * <li>When it is contained in GVL purposes we reject REQUIRE_LEGITIMATE_INTEREST {@link RestrictionType}
     * and check purposeConsent and vendorConsent;</li>
     * <li>When it is contained in GVL LegIntPurposes we reject REQUIRE_CONSENT {@link RestrictionType}
     * and check purposesLITransparency and vendorLegitimateInterest.</li>
     * <p><br>
     * If it is flexible we check by {@link RestrictionType}:
     * <li>For REQUIRE_CONSENT we check by purposeConsent and vendorConsent</li>
     * <li>For REQUIRE_LEGITIMATE_INTEREST we check by purposesLITransparency and vendorLegitimateInterest</li>
     * <li>For UNDEFINED we check by purposeConsent and vendorConsent
     * or purposesLITransparency and vendorLegitimateInterest</li>
     * <p>
     */
    private boolean isAllowedByPublisherRestrictionAndFlexible(Purpose purpose,
                                                               boolean isEnforceVendor,
                                                               VendorPermissionWithGvl vendorPermissionWithGvl,
                                                               TCString tcString,
                                                               RestrictionType restrictionType) {

        if (restrictionType.equals(RestrictionType.NOT_ALLOWED)) {
            return false;
        }

        final Integer vendorId = vendorPermissionWithGvl.getVendorPermission().getVendorId();
        final VendorV2 vendorGvl = vendorPermissionWithGvl.getVendorV2();

        final EnumSet<Purpose> flexiblePurposes = vendorGvl.getFlexiblePurposes();
        final boolean isFlexible = CollectionUtils.isNotEmpty(flexiblePurposes) && flexiblePurposes.contains(purpose);

        final EnumSet<Purpose> gvlPurposes = vendorGvl.getPurposes();
        if (gvlPurposes != null && gvlPurposes.contains(purpose)) {
            return isFlexible
                    ? isAllowedByFlexible(purpose, vendorId, isEnforceVendor, tcString, restrictionType)
                    : isAllowedByNotFlexiblePurpose(purpose, vendorId, isEnforceVendor, tcString, restrictionType);
        }

        final EnumSet<Purpose> legIntGvlPurposes = vendorGvl.getLegIntPurposes();
        if (legIntGvlPurposes != null && legIntGvlPurposes.contains(purpose)) {
            return isFlexible
                    ? isAllowedByFlexible(purpose, vendorId, isEnforceVendor, tcString, restrictionType)
                    : isAllowedByNotFlexibleLegitimateInterest(purpose, vendorId, isEnforceVendor, tcString,
                            restrictionType);
        }

        return false;
    }

    private boolean isAllowedByNotFlexiblePurpose(Purpose purpose,
                                                  Integer vendorId,
                                                  boolean isEnforceVendor,
                                                  TCString tcString,
                                                  RestrictionType restrictionType) {
        final boolean isSupportedRestriction = restrictionType.equals(RestrictionType.REQUIRE_CONSENT)
                || restrictionType.equals(RestrictionType.UNDEFINED);

        return isSupportedRestriction && isAllowedBySimpleConsent(purpose, vendorId, isEnforceVendor, tcString);
    }

    private boolean isAllowedByNotFlexibleLegitimateInterest(Purpose purpose,
                                                             Integer vendorId,
                                                             boolean isEnforceVendor,
                                                             TCString tcString,
                                                             RestrictionType restrictionType) {
        final boolean isSupportedRestriction = restrictionType.equals(RestrictionType.REQUIRE_LEGITIMATE_INTEREST)
                || restrictionType.equals(RestrictionType.UNDEFINED);

        return isSupportedRestriction && isAllowedByLegitimateInterest(purpose, vendorId, isEnforceVendor, tcString);
    }

    private boolean isAllowedByFlexible(Purpose purpose,
                                        Integer vendorId,
                                        boolean isEnforceVendor,
                                        TCString tcString,
                                        RestrictionType restrictionType) {

        switch (restrictionType) {
            case REQUIRE_CONSENT:
                return isAllowedBySimpleConsent(purpose, vendorId, isEnforceVendor, tcString);
            case REQUIRE_LEGITIMATE_INTEREST:
                return isAllowedByLegitimateInterest(purpose, vendorId, isEnforceVendor, tcString);
            case UNDEFINED:
                return isAllowedBySimpleConsentOrLegitimateInterest(purpose, vendorId, isEnforceVendor, tcString);
            default:
                return false;
        }
    }
}

