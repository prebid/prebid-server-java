package org.prebid.server.bidder.boldwin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.boldwin.proto.BoldwinImpExtBidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.boldwin.ExtImpBoldwin;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class BoldwinBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpBoldwin>> BOLDWIN_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public BoldwinBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> outgoingRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            final ExtImpBoldwin extImpBoldwin;
            try {
                extImpBoldwin = parseImpExt(imp);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
            outgoingRequests.add(createSingleRequest(modifyImp(imp, extImpBoldwin), request));
        }

        return Result.withValues(outgoingRequests);
    }

    private ExtImpBoldwin parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), BOLDWIN_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp modifyImp(Imp imp, ExtImpBoldwin extImpBoldwin) {
        final BoldwinImpExtBidder boldwinImpExtBidderWithType = getImpExtBoldwinWithType(extImpBoldwin);
        final ObjectNode modifiedImpExtBidder = mapper.mapper().createObjectNode();

        modifiedImpExtBidder.set("bidder", mapper.mapper().valueToTree(boldwinImpExtBidderWithType));

        return imp.toBuilder()
                .ext(modifiedImpExtBidder)
                .build();
    }

    private BoldwinImpExtBidder getImpExtBoldwinWithType(ExtImpBoldwin extImpBoldwin) {
        final BoldwinImpExtBidder.BoldwinImpExtBidderBuilder impExtBoldwin = BoldwinImpExtBidder.builder();

        if (StringUtils.isNotEmpty(extImpBoldwin.getPlacementId())) {
            impExtBoldwin
                    .type("publisher")
                    .placementId(extImpBoldwin.getPlacementId());
        } else if (StringUtils.isNotEmpty(extImpBoldwin.getEndpointId())) {
            impExtBoldwin
                    .type("network")
                    .endpointId(extImpBoldwin.getEndpointId());
        }

        return impExtBoldwin.build();
    }

    private HttpRequest<BidRequest> createSingleRequest(Imp imp, BidRequest request) {
        final BidRequest outgoingRequest = request.toBuilder().imp(Collections.singletonList(imp)).build();

        return BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid), bidResponse.getCur()))
                .toList();
    }

    private static BidType getBidType(Bid bid) {
        final int mType = bid.getMtype() != null ? bid.getMtype() : 999;
        return switch (mType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException(
                    "Unable to fetch mediaType in multi-format: %s".formatted(bid.getImpid())
            );
        };
    }
}
