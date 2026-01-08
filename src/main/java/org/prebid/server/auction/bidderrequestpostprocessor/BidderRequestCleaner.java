package org.prebid.server.auction.bidderrequestpostprocessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAlternateBidderCodes;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAlternateBidderCodesBidder;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

public class BidderRequestCleaner implements BidderRequestPostProcessor {

    @Override
    public Future<Result<BidderRequest>> process(BidderRequest bidderRequest,
                                                 BidderAliases aliases,
                                                 AuctionContext auctionContext) {

        final BidRequest bidRequest = bidderRequest.getBidRequest();
        final UpdateResult<ExtRequest> cleanedExt = cleanExt(bidRequest.getExt(), bidderRequest.getBidder());

        final BidderRequest cleanedBidderRequest = cleanedExt.isUpdated()
                ? bidderRequest.with(bidRequest.toBuilder().ext(cleanedExt.getValue()).build())
                : bidderRequest;

        return Future.succeededFuture(Result.of(cleanedBidderRequest, Collections.emptyList()));
    }

    private UpdateResult<ExtRequest> cleanExt(ExtRequest ext, String bidder) {
        final ExtRequestPrebid extPrebid = ext != null ? ext.getPrebid() : null;
        if (extPrebid == null) {
            return UpdateResult.unaltered(ext);
        }

        final UpdateResult<ExtRequestBidAdjustmentFactors> cleanedBidAdjustmentFactors =
                cleanBidAdjustmentFactors(extPrebid.getBidadjustmentfactors(), bidder);
        final UpdateResult<ObjectNode> cleanedBidAdjustments =
                cleanBidAdjustments(extPrebid.getBidadjustments(), bidder);
        final UpdateResult<ExtRequestPrebidAlternateBidderCodes> cleanedAlternateCodes =
                cleanAlternateCodes(extPrebid.getAlternateBidderCodes(), bidder);

        if (!cleanedBidAdjustmentFactors.isUpdated()
                && !cleanedBidAdjustments.isUpdated()
                && !cleanedAlternateCodes.isUpdated()
                && ObjectUtils.allNull(
                extPrebid.getReturnallbidstatus(),
                extPrebid.getAliasgvlids(),
                extPrebid.getAdservertargeting(),
                extPrebid.getCache(),
                extPrebid.getEvents(),
                extPrebid.getNosale(),
                extPrebid.getBiddercontrols(),
                extPrebid.getAnalytics(),
                extPrebid.getPassthrough(),
                extPrebid.getKvps())) {

            return UpdateResult.unaltered(ext);
        }

        final ExtRequest cleanedExt = ExtRequest.of(extPrebid.toBuilder()
                .returnallbidstatus(null)
                .aliasgvlids(null)
                .bidadjustmentfactors(cleanedBidAdjustmentFactors.getValue())
                .bidadjustments(cleanedBidAdjustments.getValue())
                .adservertargeting(null)
                .cache(null)
                .events(null)
                .nosale(null)
                .biddercontrols(null)
                .analytics(null)
                .passthrough(null)
                .kvps(null)
                .alternateBidderCodes(cleanedAlternateCodes.getValue())
                .build());
        cleanedExt.addProperties(ext.getProperties());

        return UpdateResult.updated(cleanedExt);
    }

    private static UpdateResult<ExtRequestBidAdjustmentFactors> cleanBidAdjustmentFactors(
            ExtRequestBidAdjustmentFactors bidAdjustmentFactors,
            String bidder) {

        if (bidAdjustmentFactors == null) {
            return UpdateResult.unaltered(null);
        }

        final Map<String, BigDecimal> cleanedAdjustments =
                cleanBidderMap(bidAdjustmentFactors.getAdjustments(), bidder);
        final EnumMap<ImpMediaType, Map<String, BigDecimal>> cleanedMediaTypes =
                cleanMediaTypes(bidAdjustmentFactors.getMediatypes(), bidder);

        if (cleanedAdjustments == null && cleanedMediaTypes == null) {
            return UpdateResult.updated(null);
        }

        final ExtRequestBidAdjustmentFactors cleanedBidAdjustmentFactors = ExtRequestBidAdjustmentFactors.builder()
                .mediatypes(cleanedMediaTypes)
                .build();
        if (cleanedAdjustments != null) {
            cleanedAdjustments.forEach(cleanedBidAdjustmentFactors::addFactor);
        }

        return UpdateResult.updated(cleanedBidAdjustmentFactors);
    }

    private static <T> Map<String, T> cleanBidderMap(Map<String, T> map, String bidder) {
        if (map == null) {
            return null;
        }

        for (Map.Entry<String, T> entry : map.entrySet()) {
            if (StringUtils.equalsIgnoreCase(entry.getKey(), bidder)) {
                return Collections.singletonMap(entry.getKey(), entry.getValue());
            }
        }

        return null;
    }

    private static EnumMap<ImpMediaType, Map<String, BigDecimal>> cleanMediaTypes(
            EnumMap<ImpMediaType, Map<String, BigDecimal>> mediaTypes,
            String bidder) {

        if (mediaTypes == null) {
            return null;
        }

        final EnumMap<ImpMediaType, Map<String, BigDecimal>> cleanedMediaTypes = new EnumMap<>(ImpMediaType.class);
        for (Map.Entry<ImpMediaType, Map<String, BigDecimal>> entry : mediaTypes.entrySet()) {
            final Map<String, BigDecimal> cleanedMap = cleanBidderMap(entry.getValue(), bidder);
            if (cleanedMap != null) {
                cleanedMediaTypes.put(entry.getKey(), cleanedMap);
            }
        }

        return !cleanedMediaTypes.isEmpty() ? cleanedMediaTypes : null;
    }

    private static UpdateResult<ObjectNode> cleanBidAdjustments(ObjectNode bidAdjustments, String bidder) {
        if (bidAdjustments == null) {
            return UpdateResult.unaltered(null);
        }

        final ObjectNode cleanedBidAdjustments = bidAdjustments.deepCopy();
        final JsonNode mediaTypeToBidAdjustments = cleanedBidAdjustments.path("mediatype");
        for (Iterator<JsonNode> maps = mediaTypeToBidAdjustments.elements(); maps.hasNext(); ) {
            final JsonNode bidderMap = maps.next();
            if (!bidderMap.isObject()) {
                continue;
            }

            for (Iterator<String> bidders = bidderMap.fieldNames(); bidders.hasNext(); ) {
                if (!StringUtils.equalsIgnoreCase(bidders.next(), bidder)) {
                    bidders.remove();
                }
            }

            if (bidderMap.isEmpty()) {
                maps.remove();
            }
        }

        return !mediaTypeToBidAdjustments.isEmpty()
                ? UpdateResult.updated(cleanedBidAdjustments)
                : UpdateResult.updated(null);
    }

    private static UpdateResult<ExtRequestPrebidAlternateBidderCodes> cleanAlternateCodes(
            ExtRequestPrebidAlternateBidderCodes alternateBidderCodes,
            String bidder) {

        final Map<String, ExtRequestPrebidAlternateBidderCodesBidder> bidders = alternateBidderCodes != null
                ? alternateBidderCodes.getBidders()
                : null;
        if (bidders == null) {
            return UpdateResult.unaltered(alternateBidderCodes);
        }

        return UpdateResult.updated(ExtRequestPrebidAlternateBidderCodes.of(
                alternateBidderCodes.getEnabled(),
                cleanBidderMap(bidders, bidder)));
    }
}
