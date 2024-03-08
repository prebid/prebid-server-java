package org.prebid.server.bidder.iqzone;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.iqzone.ExtImpIqzone;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class IqzoneBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpIqzone>> IQZONE_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final JacksonMapper mapper;
    private final String endpointUrl;

    public IqzoneBidder(String endpointUrl, JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.endpointUrl = HttpUtil.validateUrl(endpointUrl);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpIqzone extImpIqzone = parseImpExt(imp);
                final Imp modifiedImp = modifyImp(imp, extImpIqzone);

                httpRequests.add(makeHttpRequest(request, modifiedImp));
            } catch (IllegalArgumentException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.withValues(httpRequests);
    }

    private ExtImpIqzone parseImpExt(Imp imp) {
        return mapper.mapper().convertValue(imp.getExt(), IQZONE_EXT_TYPE_REFERENCE).getBidder();
    }

    private Imp modifyImp(Imp imp, ExtImpIqzone impExt) {
        final String placementId = impExt.getPlacementId();
        final ObjectNode modifiedImpExtBidder = mapper.mapper().createObjectNode();

        if (StringUtils.isNotEmpty(placementId)) {
            modifiedImpExtBidder.set("placementId", TextNode.valueOf(placementId));
            modifiedImpExtBidder.set("type", TextNode.valueOf("publisher"));
        } else {
            modifiedImpExtBidder.set("endpointId", TextNode.valueOf(impExt.getEndpointId()));
            modifiedImpExtBidder.set("type", TextNode.valueOf("network"));
        }

        return imp.toBuilder()
                .ext(mapper.mapper().createObjectNode().set("bidder", modifiedImpExtBidder))
                .build();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, Imp imp) {
        final BidRequest outgoingRequest = request.toBuilder().imp(List.of(imp)).build();

        return BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidsFromResponse(bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidMediaType(bid), bidResponse.getCur()))
                .toList();
    }

    private static BidType getBidMediaType(Bid bid) {
        final Integer markupType = bid.getMtype();
        if (markupType == null) {
            throw new PreBidException("Missing MType for bid: " + bid.getId());
        }

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException(
                    "Unable to fetch mediaType " + bid.getMtype() + " in multi-format: " + bid.getImpid());
        };
    }
}
