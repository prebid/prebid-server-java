package org.prebid.server.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtAnalytics;
import org.prebid.server.proto.openrtb.ext.response.ExtAnalyticsTags;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAnalyticsConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AnalyticsTagsEnricher {

    private AnalyticsTagsEnricher() {
    }

    public static AuctionContext enrichWithAnalyticsTags(AuctionContext context) {
        final boolean clientDetailsEnabled = isClientDetailsEnabled(context);
        if (!clientDetailsEnabled) {
            return context;
        }

        final boolean allowClientDetails = Optional.ofNullable(context.getAccount())
                .map(Account::getAnalytics)
                .map(AccountAnalyticsConfig::isAllowClientDetails)
                .orElse(false);

        if (!allowClientDetails) {
            return addClientDetailsWarning(context);
        }

        final List<ExtAnalyticsTags> extAnalyticsTags = HookDebugInfoEnricher.toExtAnalyticsTags(context);

        if (extAnalyticsTags == null) {
            return context;
        }

        final BidResponse bidResponse = context.getBidResponse();
        final Optional<ExtBidResponse> ext = Optional.ofNullable(bidResponse.getExt());
        final Optional<ExtBidResponsePrebid> extPrebid = ext.map(ExtBidResponse::getPrebid);

        final ExtBidResponsePrebid updatedExtPrebid = extPrebid
                .map(ExtBidResponsePrebid::toBuilder)
                .orElse(ExtBidResponsePrebid.builder())
                .analytics(ExtAnalytics.of(extAnalyticsTags))
                .build();

        final ExtBidResponse updatedExt = ext
                .map(ExtBidResponse::toBuilder)
                .orElse(ExtBidResponse.builder())
                .prebid(updatedExtPrebid)
                .build();

        final BidResponse updatedBidResponse = bidResponse.toBuilder().ext(updatedExt).build();
        return context.with(updatedBidResponse);
    }

    private static boolean isClientDetailsEnabled(AuctionContext context) {
        final JsonNode analytics = Optional.ofNullable(context.getBidRequest())
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getAnalytics)
                .orElse(null);

        if (notObjectNode(analytics)) {
            return false;
        }

        final JsonNode options = analytics.get("options");
        if (notObjectNode(options)) {
            return false;
        }

        final JsonNode enableClientDetails = options.get("enableclientdetails");
        return enableClientDetails != null
                && enableClientDetails.isBoolean()
                && enableClientDetails.asBoolean();
    }

    private static boolean notObjectNode(JsonNode jsonNode) {
        return jsonNode == null || !jsonNode.isObject();
    }

    private static AuctionContext addClientDetailsWarning(AuctionContext context) {
        final BidResponse bidResponse = context.getBidResponse();
        final Optional<ExtBidResponse> ext = Optional.ofNullable(bidResponse.getExt());

        final Map<String, List<ExtBidderError>> warnings = ext
                .map(ExtBidResponse::getWarnings)
                .orElse(Collections.emptyMap());
        final List<ExtBidderError> prebidWarnings = ObjectUtils.defaultIfNull(
                warnings.get(BidResponseCreator.DEFAULT_DEBUG_KEY),
                Collections.emptyList());

        final List<ExtBidderError> updatedPrebidWarnings = new ArrayList<>(prebidWarnings);
        updatedPrebidWarnings.add(ExtBidderError.of(
                BidderError.Type.generic.getCode(),
                "analytics.options.enableclientdetails not enabled for account"));
        final Map<String, List<ExtBidderError>> updatedWarnings = new HashMap<>(warnings);
        updatedWarnings.put(BidResponseCreator.DEFAULT_DEBUG_KEY, updatedPrebidWarnings);

        final ExtBidResponse updatedExt = ext
                .map(ExtBidResponse::toBuilder)
                .orElse(ExtBidResponse.builder())
                .warnings(updatedWarnings)
                .build();

        final BidResponse updatedBidResponse = bidResponse.toBuilder().ext(updatedExt).build();
        return context.with(updatedBidResponse);
    }
}
