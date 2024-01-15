package org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies;

import com.iabtcf.decoder.TCString;
import com.iabtcf.v2.RestrictionType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.DefaultedMap;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FullEnforcePurposeStrategy extends EnforcePurposeStrategy {

    public Stream<VendorPermission> allowedByTypeStrategy(PurposeCode purpose,
                                                          TCString vendorConsent,
                                                          Collection<VendorPermissionWithGvl> vendorsForPurpose,
                                                          Collection<VendorPermissionWithGvl> excludedVendors,
                                                          boolean isEnforceVendors) {

        final Map<Integer, RestrictionType> vendorToRestriction = vendorToRestriction(
                purpose, vendorConsent, vendorsForPurpose, excludedVendors);

        final Stream<VendorPermission> allowedExcluded = toVendorPermissions(excludedVendors)
                .filter(vendorPermission -> isNotRestricted(vendorPermission, vendorToRestriction));

        final Stream<VendorPermission> allowedVendorPermissions = vendorsForPurpose.stream()
                .filter(vendorPermissionWithGvl -> isAllowedByPublisherRestrictionAndFlexible(
                        purpose,
                        isEnforceVendors,
                        vendorPermissionWithGvl,
                        vendorConsent,
                        vendorToRestriction.get(vendorPermissionWithGvl.getVendorPermission().getVendorId())))
                .map(VendorPermissionWithGvl::getVendorPermission);

        return Stream.concat(allowedExcluded, allowedVendorPermissions);
    }

    private static Map<Integer, RestrictionType> vendorToRestriction(
            PurposeCode purpose,
            TCString vendorConsent,
            Collection<VendorPermissionWithGvl> vendorsForPurpose,
            Collection<VendorPermissionWithGvl> excludedVendors) {

        final Set<Integer> participatingVendorsIds =
                Stream.concat(vendorsForPurpose.stream(), excludedVendors.stream())
                        .map(VendorPermissionWithGvl::getVendorPermission)
                        .map(VendorPermission::getVendorId)
                        .collect(Collectors.toSet());

        final Map<Integer, RestrictionType> publisherRestrictions = new HashMap<>();
        vendorConsent.getPublisherRestrictions().stream()
                .filter(publisherRestriction -> publisherRestriction.getPurposeId() == purpose.code())
                .forEach(publisherRestriction -> publisherRestriction.getVendorIds().toStream()
                        .filter(participatingVendorsIds::contains)
                        .forEach(vendorId -> publisherRestrictions.merge(
                                vendorId,
                                publisherRestriction.getRestrictionType(),
                                (first, second) -> second == RestrictionType.NOT_ALLOWED ? second : first)));

        return DefaultedMap.defaultedMap(publisherRestrictions, RestrictionType.UNDEFINED);
    }

    private boolean isNotRestricted(VendorPermission vendorPermission,
                                    Map<Integer, RestrictionType> vendorToRestriction) {

        final Integer vendorId = vendorPermission.getVendorId();
        return vendorId == null || vendorToRestriction.get(vendorId) != RestrictionType.NOT_ALLOWED;
    }

    /**
     * Purpose is flexible when {@link Vendor} flexiblePurposes contains it.
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
    private boolean isAllowedByPublisherRestrictionAndFlexible(PurposeCode purpose,
                                                               boolean isEnforceVendor,
                                                               VendorPermissionWithGvl vendorPermissionWithGvl,
                                                               TCString tcString,
                                                               RestrictionType restrictionType) {

        if (restrictionType == RestrictionType.NOT_ALLOWED) {
            return false;
        }

        final Integer vendorId = vendorPermissionWithGvl.getVendorPermission().getVendorId();
        final Vendor vendorGvl = vendorPermissionWithGvl.getVendor();

        final EnumSet<PurposeCode> flexiblePurposes = vendorGvl.getFlexiblePurposes();
        final boolean isFlexible = CollectionUtils.isNotEmpty(flexiblePurposes) && flexiblePurposes.contains(purpose);

        final EnumSet<PurposeCode> gvlPurposeCodes = vendorGvl.getPurposes();
        if (gvlPurposeCodes != null && gvlPurposeCodes.contains(purpose)) {
            return isFlexible
                    ? isAllowedByFlexible(purpose, vendorId, isEnforceVendor, tcString, restrictionType)
                    : isAllowedByNotFlexiblePurpose(purpose, vendorId, isEnforceVendor, tcString, restrictionType);
        }

        final EnumSet<PurposeCode> legIntGvlPurposeCodes = vendorGvl.getLegIntPurposes();
        if (legIntGvlPurposeCodes != null && legIntGvlPurposeCodes.contains(purpose)) {
            return isFlexible
                    ? isAllowedByFlexible(purpose, vendorId, isEnforceVendor, tcString, restrictionType)
                    : isAllowedByNotFlexibleLegitimateInterest(
                    purpose, vendorId, isEnforceVendor, tcString, restrictionType);
        }

        return false;
    }

    private boolean isAllowedByNotFlexiblePurpose(PurposeCode purpose,
                                                  Integer vendorId,
                                                  boolean isEnforceVendor,
                                                  TCString tcString,
                                                  RestrictionType restrictionType) {

        return (restrictionType == RestrictionType.REQUIRE_CONSENT || restrictionType == RestrictionType.UNDEFINED)
                && isAllowedBySimpleConsent(purpose, vendorId, isEnforceVendor, tcString);
    }

    private boolean isAllowedByNotFlexibleLegitimateInterest(PurposeCode purpose,
                                                             Integer vendorId,
                                                             boolean isEnforceVendor,
                                                             TCString tcString,
                                                             RestrictionType restrictionType) {

        final boolean isSupportedRestriction = restrictionType.equals(RestrictionType.REQUIRE_LEGITIMATE_INTEREST)
                || restrictionType.equals(RestrictionType.UNDEFINED);

        return isSupportedRestriction && isAllowedByLegitimateInterest(purpose, vendorId, isEnforceVendor, tcString);
    }

    private boolean isAllowedByFlexible(PurposeCode purpose,
                                        Integer vendorId,
                                        boolean isEnforceVendor,
                                        TCString tcString,
                                        RestrictionType restrictionType) {

        return switch (restrictionType) {
            case NOT_ALLOWED -> false;
            case REQUIRE_CONSENT -> isAllowedBySimpleConsent(purpose, vendorId, isEnforceVendor, tcString);
            case REQUIRE_LEGITIMATE_INTEREST ->
                    isAllowedByLegitimateInterest(purpose, vendorId, isEnforceVendor, tcString);
            case UNDEFINED ->
                    isAllowedBySimpleConsentOrLegitimateInterest(purpose, vendorId, isEnforceVendor, tcString);
        };
    }
}
