package org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies;

import com.iabtcf.decoder.TCString;
import com.iabtcf.v2.PublisherRestriction;
import com.iabtcf.v2.RestrictionType;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.privacy.gdpr.model.PublisherRestrictionEmpty;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorV2;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FullEnforcePurposeStrategy extends EnforcePurposeStrategy {

    public Collection<VendorPermission> allowedByTypeStrategy(int purposeId,
                                                              TCString vendorConsent,
                                                              Collection<VendorPermissionWithGvl> vendorsForPurpose,
                                                              Collection<VendorPermissionWithGvl> excludedVendors,
                                                              boolean isEnforceVendors) {

        final PublisherRestriction publisherRestrictions = vendorConsent.getPublisherRestrictions().stream()
                .filter(publisherRestriction -> publisherRestriction.getPurposeId() == purposeId)
                .findFirst()
                .orElse(new PublisherRestrictionEmpty(purposeId));

        if (publisherRestrictions.getRestrictionType().equals(RestrictionType.NOT_ALLOWED)) {
            return Collections.emptyList();
        }

        final List<VendorPermission> allowedVendorPermissions = vendorsForPurpose.stream()
                .filter(vendorPermission -> vendorPermission.getVendorPermission().getVendorId() != null)
                .filter(vendorPermission -> isAllowedByPublisherRestrictionAndFlexible(purposeId, isEnforceVendors,
                        vendorPermission, vendorConsent, publisherRestrictions))
                .map(VendorPermissionWithGvl::getVendorPermission)
                .collect(Collectors.toList());

        return CollectionUtils.union(toVendorPermissions(excludedVendors), allowedVendorPermissions);
    }

    /**
     * Purpose is flexible when {@link VendorV2} flexiblePurposes contains it id.
     * When it is not flexible:
     * We check purposeConsent and vendorConsent when it is contained in GVL purposes;
     * We check purposesLITransparency and vendorLegitimateInterest when it is contained in GVL LegIntPurposes.
     * <p><br>
     * If it flexible we check by {@link RestrictionType}:
     * <li>For REQUIRE_CONSENT we check by purposeConsent and vendorConsent</li>
     * <li>For REQUIRE_LEGITIMATE_INTEREST we check by purposesLITransparency and vendorLegitimateInterest</li>
     * <li>For UNDEFINED we check by purposeConsent and vendorConsent
     * or purposesLITransparency and vendorLegitimateInterest</li>
     * <p>
     */
    private boolean isAllowedByPublisherRestrictionAndFlexible(int purposeId,
                                                               boolean isEnforceVendor,
                                                               VendorPermissionWithGvl vendorPermissionWithGvl,
                                                               TCString tcString,
                                                               PublisherRestriction publisherRestriction) {
        final Integer vendorId = vendorPermissionWithGvl.getVendorPermission().getVendorId();
        final VendorV2 vendorGvl = vendorPermissionWithGvl.getVendorV2();

        final RestrictionType restrictionType = publisherRestriction.getRestrictionType();
        final boolean isFlexible = vendorGvl.getFlexiblePurposes().contains(purposeId);

        final Set<Integer> gvlPurposes = vendorGvl.getPurposes();
        if (gvlPurposes != null && gvlPurposes.contains(purposeId)) {
            return isFlexible
                    ? isAllowedByRestrictionTypePurpose(purposeId, vendorId, isEnforceVendor, tcString, restrictionType)
                    : isAllowedBySimpleConsent(purposeId, vendorId, isEnforceVendor, tcString);
        }

        final Set<Integer> legIntGvlPurposes = vendorGvl.getLegIntPurposes();
        if (legIntGvlPurposes != null && legIntGvlPurposes.contains(purposeId)) {
            return isFlexible
                    ? isAllowedByRestrictionTypePurpose(purposeId, vendorId, isEnforceVendor, tcString, restrictionType)
                    : isAllowedByLegitimateInterest(purposeId, vendorId, isEnforceVendor, tcString);
        }
        return false;
    }

    private boolean isAllowedByRestrictionTypePurpose(int purposeId,
                                                      Integer vendorId,
                                                      boolean isEnforceVendor,
                                                      TCString tcString,
                                                      RestrictionType restrictionType) {
        switch (restrictionType) {
            case REQUIRE_CONSENT:
                return isAllowedBySimpleConsent(purposeId, vendorId, isEnforceVendor, tcString);
            case REQUIRE_LEGITIMATE_INTEREST:
                return isAllowedByLegitimateInterest(purposeId, vendorId, isEnforceVendor, tcString);
            case UNDEFINED:
                return isAllowedBySimpleConsentOrLegitimateInterest(purposeId, vendorId, isEnforceVendor, tcString);
            default:
                return false;
        }
    }
}

