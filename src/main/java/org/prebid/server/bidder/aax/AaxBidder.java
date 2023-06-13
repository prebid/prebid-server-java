package org.prebid.server.bidder.aax;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AaxBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    private static final String USD_CURRENCY = "USD";

    public AaxBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        return Result.withValue(BidderUtil.defaultRequest(bidRequest, endpointUrl, mapper));
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            List<BidderBid> bidderBids = extractBids(httpCall.getRequest().getPayload(), bidResponse, errors);
            return Result.of(bidderBids, errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse, List<BidderError> errorList) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse, errorList);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest,
                                             BidResponse bidResponse,
                                             List<BidderError> errorList) {

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> resolveBidderBid(bid, bidRequest.getImp(), errorList))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private BidderBid resolveBidderBid(Bid bid, List<Imp> imps, List<BidderError> errors) {
        final BidType bidType;
        try {
            bidType = getBidType(bid, imps);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
        return BidderBid.of(bid, bidType, USD_CURRENCY);
    }

    private BidType getBidType(Bid bid, List<Imp> imps) {
        return ObjectUtil.firstNonNull(
                () -> bidTypeFromExt(bid.getExt()),
                () -> bidTypeFromImp(bid.getImpid(), imps));
    }

    private static BidType bidTypeFromExt(ObjectNode bidExt) {
        final JsonNode bidTypeNode = bidExt != null ? bidExt.get("adCodeType") : null;
        final String bidType = bidTypeNode != null ? bidTypeNode.textValue() : null;
        if (bidType == null) {
            return null;
        }

        return switch (bidType) {
            case "banner" -> BidType.banner;
            case "native" -> BidType.xNative;
            case "video" -> BidType.video;
            default -> null;
        };
    }

    private static BidType bidTypeFromImp(String impId, List<Imp> imps) {
        BidType bidType = null;
        int counter = 0;
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    counter++;
                    bidType = BidType.banner;
                }
                if (imp.getVideo() != null) {
                    counter++;
                    bidType = BidType.video;
                }
                if (imp.getXNative() != null) {
                    counter++;
                    bidType = BidType.xNative;
                }
            }
        }

        if (counter != 1) {
            throw new PreBidException(String.format("Unable to fetch mediaType in multi-format: %s", impId));
        }

        return bidType;
    }
}
