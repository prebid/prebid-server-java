package org.prebid.server.auction.my;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;
import org.prebid.server.gdpr.GdprService;
import org.prebid.server.gdpr.model.GdprResponse;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Gdpr {

    GdprService gdprService;

    /**
     * Returns {@link Future &lt;{@link Map}&lt;{@link Integer}, {@link Boolean}&gt;&gt;}, where bidders vendor id mapped
     * to enabling or disabling GDPR in scope of pbs server. If bidder vendor id is not present in map, it means that
     * pbs not enforced particular bidder to follow pbs GDPR procedure.
     */
    public Future<Map<Integer, Boolean>> getVendorsToGdprPermission(BidRequest bidRequest, List<String> bidders,
                                                                     Map<String, String> aliases,
                                                                     String publisherId, ExtUser extUser,
                                                                     ExtRegs extRegs, Timeout timeout) {
        final Integer gdpr = extRegs != null ? extRegs.getGdpr() : null;
        final String gdprAsString = gdpr != null ? gdpr.toString() : null;
        final String gdprConsent = extUser != null ? extUser.getConsent() : null;
        final Device device = bidRequest.getDevice();
        final String ipAddress = useGeoLocation && device != null ? device.getIp() : null;
        final Set<Integer> vendorIds = extractGdprEnforcedVendors(bidders, aliases);

        return gdprService.isGdprEnforced(gdprAsString, publisherId, vendorIds, timeout)
                .compose(gdprEnforced -> !gdprEnforced
                        ? Future.succeededFuture(Collections.emptyMap())
                        : gdprService.resultByVendor(vendorIds, gdprAsString, gdprConsent, ipAddress, timeout)
                        .map(GdprResponse::getVendorsToGdpr));
    }

    /**
     * Returns flag if GDPR masking is required for bidder.
     */
    public boolean isGdprMaskingRequiredFor(String bidder, Map<String, String> aliases,
                                             Map<Integer, Boolean> vendorToGdprPermission, Integer deviceLmt) {
        final boolean maskingRequired;
        final boolean isLmtEnabled = deviceLmt != null && deviceLmt.equals(1);
        if (vendorToGdprPermission.isEmpty() && !isLmtEnabled) {
            maskingRequired = false;
        } else {
            final String resolvedBidderName = resolveBidder(bidder, aliases);
            final int vendorId = bidderCatalog.bidderInfoByName(resolvedBidderName).getGdpr().getVendorId();
            final Boolean gdprAllowsUserData = vendorToGdprPermission.get(vendorId);

            // if bidder was not found in vendorToGdprPermission, it means that it was not enforced for GDPR,
            // so request for this bidder should be sent without changes
            maskingRequired = (gdprAllowsUserData != null && !gdprAllowsUserData) || isLmtEnabled;

            if (maskingRequired) {
                metrics.updateGdprMaskedMetric(resolvedBidderName);
            }
        }
        return maskingRequired;
    }


    /**
     * Extracts GDPR enforced vendor IDs.
     */
    private Set<Integer> extractGdprEnforcedVendors(List<String> bidders, Map<String, String> aliases) {
        return bidders.stream()
                .map(bidder -> bidderCatalog.bidderInfoByName(resolveBidder(bidder, aliases)).getGdpr())
                .filter(BidderInfo.GdprInfo::isEnforced)
                .map(BidderInfo.GdprInfo::getVendorId)
                .collect(Collectors.toSet());
    }


}
