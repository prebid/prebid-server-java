package org.prebid.server.bidder.aax;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.aax.model.AaxResponseBidExt;
import org.prebid.server.bidder.model.*;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AaxBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AaxBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        return Result.withValue(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .headers(HttpUtil.headers())
                        .body(mapper.encodeToBytes(bidRequest))
                        .payload(bidRequest)
                        .build());
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

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse, List<BidderError> errorList) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> resolveBidderBid(bid, bidResponse.getCur(), bidRequest.getImp(), errorList))
                .collect(Collectors.toList());
    }

    private BidderBid resolveBidderBid(Bid bid, String cur, List<Imp> imps, List<BidderError> errors) {
        final BidType bidType;
        try {
            bidType = getBidType(bid, imps);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
        return BidderBid.of(bid, bidType, cur);
    }

    private BidType getBidType(Bid bid, List<Imp> imps) {
        final AaxResponseBidExt aaxExtBid = bid.getExt() != null ? getMappedBidExt(bid.getExt()) : null;
        final String adCodeType = aaxExtBid != null ? aaxExtBid.getAdCodeType() : null;

        if (adCodeType != null && getBidTypeByText(adCodeType) != null) {
            return getBidTypeByText(adCodeType);
        }

        BidType bidType = null;
        long countType = 0;
        for (Imp imp : imps) {
            if (imp.getId().equals(bid.getImpid())) {
                if (imp.getBanner() != null) {
                    countType++;
                    bidType = BidType.banner;
                }
                if (imp.getVideo() != null) {
                    countType++;
                    bidType = BidType.video;
                }
                if (imp.getXNative() != null) {
                    countType++;
                    bidType = BidType.xNative;
                }
            }
        }

        if (countType == 1) {
            return bidType;
        }

        throw new PreBidException(String.format("Unable to fetch mediaType in multi-format: %s", bid.getImpid()));
    }

    private AaxResponseBidExt getMappedBidExt(ObjectNode objectNode) {
        try {
            return mapper.mapper().convertValue(objectNode, AaxResponseBidExt.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private BidType getBidTypeByText(String mediaType) {
        switch (mediaType) {
            case "banner":
                return BidType.banner;
            case "native":
                return BidType.xNative;
            case "video":
                return BidType.video;
            default:
                return null;
        }
    }
}
