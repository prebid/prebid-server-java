package org.prebid.server.hooks.modules.pb.response.correction.core.correction.appvideohtml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.response.Bid;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.hooks.modules.pb.response.correction.core.config.model.Config;
import org.prebid.server.hooks.modules.pb.response.correction.core.correction.Correction;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class AppVideoHtmlCorrection implements Correction {

    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(
            LoggerFactory.getLogger(AppVideoHtmlCorrection.class));

    private static final Pattern VAST_XML_PATTERN = Pattern.compile("<\\w*VAST\\w+", Pattern.CASE_INSENSITIVE);
    private static final TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>> EXT_BID_PREBID_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String NATIVE_ADM_MESSAGE = "Bid %s of bidder %s has an JSON ADM, that appears to be native";
    private static final String ADM_WITH_NO_ASSETS_MESSAGE = "Bid %s of bidder %s has a JSON ADM, but without assets";
    private static final String CHANGING_BID_MEDIA_TYPE_MESSAGE = "Bid %s of bidder %s: changing media type to banner";

    private final ObjectMapper mapper;
    private final double logSamplingRate;

    public AppVideoHtmlCorrection(ObjectMapper mapper, double logSamplingRate) {
        this.mapper = mapper;
        this.logSamplingRate = logSamplingRate;
    }

    @Override
    public List<BidderResponse> apply(Config config, List<BidderResponse> bidderResponses) {
        final Collection<String> excludedBidders = CollectionUtils.emptyIfNull(
                config.getAppVideoHtmlConfig().getExcludedBidders());

        return bidderResponses.stream()
                .map(response -> modify(response, excludedBidders))
                .toList();
    }

    private BidderResponse modify(BidderResponse response, Collection<String> excludedBidders) {
        final String bidder = response.getBidder();
        if (excludedBidders.contains(bidder)) {
            return response;
        }

        final BidderSeatBid seatBid = response.getSeatBid();
        final List<BidderBid> modifiedBids = seatBid.getBids().stream()
                .map(bidderBid -> modifyBid(bidder, bidderBid))
                .toList();

        return response.with(seatBid.with(modifiedBids));
    }

    private BidderBid modifyBid(String bidder, BidderBid bidderBid) {
        final Bid bid = bidderBid.getBid();
        final String bidId = bid.getId();
        final String adm = bid.getAdm();

        if (adm == null || isVideoWithVastXml(bidderBid.getType(), adm) || hasNativeAdm(adm, bidId, bidder)) {
            return bidderBid;
        }

        conditionalLogger.warn(CHANGING_BID_MEDIA_TYPE_MESSAGE.formatted(bidId, bidder), logSamplingRate);

        final ExtBidPrebid prebid = parseExtBidPrebid(bid);

        final ExtBidPrebidMeta modifiedMeta = Optional.ofNullable(prebid)
                .map(ExtBidPrebid::getMeta)
                .map(ExtBidPrebidMeta::toBuilder)
                .orElseGet(ExtBidPrebidMeta::builder)
                .mediaType(BidType.video.getName())
                .build();

        final ExtBidPrebid modifiedPrebid = Optional.ofNullable(prebid)
                .map(ExtBidPrebid::toBuilder)
                .orElseGet(ExtBidPrebid::builder)
                .meta(modifiedMeta)
                .build();

        final ObjectNode modifiedBidExt = mapper.valueToTree(ExtPrebid.of(modifiedPrebid, null));

        return bidderBid.toBuilder()
                .type(BidType.banner)
                .bid(bid.toBuilder().ext(modifiedBidExt).build())
                .build();
    }

    private boolean hasNativeAdm(String adm, String bidId, String bidder) {
        try {
            final JsonNode jsonNode = mapper.readTree(adm);
            if (jsonNode.has("assets")) {
                conditionalLogger.warn(NATIVE_ADM_MESSAGE.formatted(bidId, bidder), logSamplingRate);
                return true;
            } else {
                conditionalLogger.warn(ADM_WITH_NO_ASSETS_MESSAGE.formatted(bidId, bidder), logSamplingRate);
                return false;
            }
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private static boolean isVideoWithVastXml(BidType type, String adm) {
        return type == BidType.video && VAST_XML_PATTERN.matcher(adm).matches();
    }

    private ExtBidPrebid parseExtBidPrebid(Bid bid) {
        try {
            return mapper.convertValue(bid.getExt(), EXT_BID_PREBID_TYPE_REFERENCE).getPrebid();
        } catch (Exception e) {
            return null;
        }
    }

}
