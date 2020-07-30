package org.prebid.server.privacy.gdpr;

import com.iab.gdpr.consent.VendorConsent;
import com.iab.gdpr.consent.VendorConsentDecoder;
import com.iab.gdpr.exception.VendorConsentParseException;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.vendorlist.VendorListService;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorListV1;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorV1;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service provides GDPR support.
 * <p>
 * For more information about GDPR, see https://gdpr.iab.com site.
 */
public class GdprService {

    private static final Logger logger = LoggerFactory.getLogger(GdprService.class);

    private static final int PURPOSE_ONE_ID = 1;

    private final VendorListService<VendorListV1, VendorV1> vendorListService;

    public GdprService(VendorListService<VendorListV1, VendorV1> vendorListService) {
        this.vendorListService = Objects.requireNonNull(vendorListService);
    }

    /**
     * Determines what is allowed and what is not (in terms of TCF v1.1 implementation aka GDPR) for each vendor
     * taking into account user consent string (version 1.1).
     *
     * @param vendorIds           to examine in consent string and vendor list
     * @param vendorConsentString user consent string
     * @return collection of {@link VendorPermission}s indicating what vendor is allowed to do
     */
    public Future<Collection<VendorPermission>> resultFor(Set<Integer> vendorIds, String vendorConsentString) {

        final VendorConsent vendorConsent = vendorConsentFrom(vendorConsentString);
        if (vendorConsent == null) {
            // consent string is broken
            return Future.succeededFuture(vendorIds.stream()
                    .map(GdprService::toRestrictedVendorPermission)
                    .collect(Collectors.toList()));
        }

        return vendorListService.forVersion(vendorConsent.getVendorListVersion())
                .compose(vendorListMapping -> toResult(vendorListMapping, vendorIds, vendorConsent),
                        ignoredFailed -> toFallbackResult(vendorIds));
    }

    /**
     * Parses consent string to {@link VendorConsent} model. Returns null if:
     * <p>
     * - consent string is missing
     * <p>
     * - parsing of consent string is failed
     */
    private VendorConsent vendorConsentFrom(String vendorConsentString) {
        if (StringUtils.isEmpty(vendorConsentString)) {
            return null;
        }
        try {
            return VendorConsentDecoder.fromBase64String(vendorConsentString);
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.info("Parsing consent string failed with error: {0}", e.getMessage());
            return null;
        }
    }

    /**
     * Processes {@link VendorListService} response and returns GDPR result for every vendor ID.
     */
    private static Future<Collection<VendorPermission>> toResult(Map<Integer, VendorV1> vendorListMapping,
                                                                 Set<Integer> vendorIds,
                                                                 VendorConsent vendorConsent) {

        final Set<Integer> allowedPurposeIds = getAllowedPurposeIdsFromConsent(vendorConsent);
        final List<VendorPermission> vendorPermissions = vendorIds.stream()
                .map(vendorId -> toVendorPermission(vendorId, vendorListMapping, vendorConsent, allowedPurposeIds))
                .collect(Collectors.toList());
        return Future.succeededFuture(vendorPermissions);
    }

    /**
     * Retrieves allowed purpose ids from consent string. Throws {@link InvalidRequestException} in case of
     * gdpr sdk throws {@link ArrayIndexOutOfBoundsException} when consent string is not valid.
     */
    private static Set<Integer> getAllowedPurposeIdsFromConsent(VendorConsent vendorConsent) {
        try {
            return vendorConsent.getAllowedPurposeIds();
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new InvalidRequestException(
                    "Error when retrieving allowed purpose ids in a reason of invalid consent string");
        }
    }

    private static VendorPermission toVendorPermission(Integer vendorId,
                                                       Map<Integer, VendorV1> vendorListMapping,
                                                       VendorConsent vendorConsent,
                                                       Set<Integer> allowedPurposeIds) {

        // confirm that there is consent for vendor and it has entry in vendor list
        if (!isVendorAllowed(vendorConsent, vendorId) || !vendorListMapping.containsKey(vendorId)) {
            return toRestrictedVendorPermission(vendorId);
        }

        final VendorV1 vendorListEntry = vendorListMapping.get(vendorId);

        // confirm purposes
        final Set<Integer> claimedPurposes = vendorListEntry.combinedPurposes();
        final boolean claimedPurposesAllowed = allowedPurposeIds.containsAll(claimedPurposes);
        final boolean purposeOneClaimedAndAllowed = isPurposeOneClaimedAndAllowed(claimedPurposes, allowedPurposeIds);

        return VendorPermission.of(vendorId, null, toAction(claimedPurposesAllowed, purposeOneClaimedAndAllowed));
    }

    private static VendorPermission toRestrictedVendorPermission(Integer vendorId) {
        return VendorPermission.of(vendorId, null, allDenied());
    }

    /**
     * Checks if vendorId is in list of allowed vendors in consent string. Throws {@link InvalidRequestException}
     * in case of gdpr sdk throws exception when consent string is not valid.
     */
    private static boolean isVendorAllowed(VendorConsent vendorConsent, Integer vendorId) {
        try {
            return vendorConsent.isVendorAllowed(vendorId);
        } catch (ArrayIndexOutOfBoundsException | VendorConsentParseException e) {
            throw new InvalidRequestException(
                    "Error when checking if vendor is allowed in a reason of invalid consent string");
        }
    }

    private static boolean isPurposeOneClaimedAndAllowed(Set<Integer> claimedPurposes, Set<Integer> allowedPurposeIds) {
        return claimedPurposes.contains(PURPOSE_ONE_ID) && allowedPurposeIds.contains(PURPOSE_ONE_ID);
    }

    private static PrivacyEnforcementAction allDenied() {
        return toAction(false, false);
    }

    private static PrivacyEnforcementAction toAction(boolean allowPrivateInfo, boolean allowUserSync) {
        return PrivacyEnforcementAction.builder()
                .removeUserIds(!allowPrivateInfo)
                .maskGeo(!allowPrivateInfo)
                .maskDeviceIp(!allowPrivateInfo)
                .maskDeviceInfo(!allowPrivateInfo)
                .blockAnalyticsReport(false)
                .blockBidderRequest(false)
                .blockPixelSync(!allowUserSync)
                .build();
    }

    private static Future<Collection<VendorPermission>> toFallbackResult(Set<Integer> vendorIds) {
        final List<VendorPermission> vendorPermissions = vendorIds.stream()
                .filter(Objects::nonNull)
                .map(GdprService::toRestrictedVendorPermission)
                .collect(Collectors.toList());
        return Future.succeededFuture(vendorPermissions);
    }
}
