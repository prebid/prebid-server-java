package org.prebid.server.bidder.loyal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.loyal.proto.LoyalImpExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.loyal.ExtImpLoyal;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class LoyalBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpLoyal>> LOYAL_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String PUBLISHER_PROPERTY = "publisher";
    private static final String NETWORK_PROPERTY = "network";
    private static final String BIDDER_PROPERTY = "bidder";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public LoyalBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpLoyal extImpLoyal = parseExtImp(imp);
                final Imp modifiedImp = modifyImp(imp, extImpLoyal);
                httpRequests.add(makeHttpRequest(request, modifiedImp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (httpRequests.isEmpty()) {
            return Result.withError(BidderError.badInput("found no valid impressions"));
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpLoyal parseExtImp(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), LOYAL_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Cannot deserialize ExtImpLoyal: " + e.getMessage());
        }
    }

    private Imp modifyImp(Imp imp, ExtImpLoyal extImpLoyal) {
        final LoyalImpExt impExtLoyalWithType = resolveImpExt(extImpLoyal);
        final ObjectNode modifiedImpExtBidder = mapper.mapper().createObjectNode();
        modifiedImpExtBidder.set(BIDDER_PROPERTY, mapper.mapper().valueToTree(impExtLoyalWithType));

        return imp.toBuilder().ext(modifiedImpExtBidder).build();
    }

    private LoyalImpExt resolveImpExt(ExtImpLoyal extImpLoyal) {
        final LoyalImpExt.LoyalImpExtBuilder builder = LoyalImpExt.builder();

        if (StringUtils.isNotEmpty(extImpLoyal.getPlacementId())) {
            builder.type(PUBLISHER_PROPERTY).placementId(extImpLoyal.getPlacementId());
        } else if (StringUtils.isNotEmpty(extImpLoyal.getEndpointId())) {
            builder.type(NETWORK_PROPERTY).endpointId(extImpLoyal.getEndpointId());
        }

        return builder.build();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, Imp imp) {
        final BidRequest outgoingRequest = request.toBuilder().imp(List.of(imp)).build();

        return BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderBid> bids = extractBids(httpCall.getRequest().getPayload(), bidResponse);
            return Result.withValues(bids);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
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

    private BidType getBidType(Bid bid) {
        final JsonNode typeNode = Optional.ofNullable(bid.getExt())
                .map(extNode -> extNode.get("prebid"))
                .map(extPrebidNode -> extPrebidNode.get("type"))
                .orElse(null);

        final BidType bidType;
        try {
            bidType = mapper.mapper().convertValue(typeNode, BidType.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to parse bid.ext.prebid.type for bid.id: '%s'"
                    .formatted(bid.getId()));
        }

        if (bidType == null) {
            throw new PreBidException("bid.ext.prebid.type is not present for bid.id: '%s'"
                    .formatted(bid.getId()));
        }

        return switch (bidType) {
            case banner, video, xNative -> bidType;
            default -> throw new PreBidException("Unsupported BidType: "
                    + bidType.getName() + " for bid.id: '" + bid.getId() + "'");
        };
    }

}
