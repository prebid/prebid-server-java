package org.prebid.server.bidder.teqblaze;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.teqblaze.proto.TeqblazeExtImp;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.teqblaze.ExtImpTeqblaze;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TeqblazeBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpTeqblaze>> EXT_TYPE_REF = new TypeReference<>() {
    };

    private static final String PUBLISHER_PROPERTY = "publisher";
    private static final String NETWORK_PROPERTY = "network";
    private static final String BIDDER_PROPERTY = "bidder";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public TeqblazeBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpTeqblaze extImpTeqblaze = parseExtImp(imp);
                final Imp modifiedImp = modifyImp(imp, extImpTeqblaze);
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

    private ExtImpTeqblaze parseExtImp(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), EXT_TYPE_REF).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Cannot deserialize ExtImpLoyal: " + e.getMessage());
        }
    }

    private Imp modifyImp(Imp imp, ExtImpTeqblaze extImpTeqblaze) {
        final TeqblazeExtImp impExtTeqblazeWithType = resolveImpExt(extImpTeqblaze);
        final ObjectNode modifiedImpExtBidder = mapper.mapper().createObjectNode();
        modifiedImpExtBidder.set(BIDDER_PROPERTY, mapper.mapper().valueToTree(impExtTeqblazeWithType));

        return imp.toBuilder().ext(modifiedImpExtBidder).build();
    }

    private TeqblazeExtImp resolveImpExt(ExtImpTeqblaze extImpTeqblaze) {
        final TeqblazeExtImp.TeqblazeExtImpBuilder builder = TeqblazeExtImp.builder();

        if (StringUtils.isNotEmpty(extImpTeqblaze.getPlacementId())) {
            builder.type(PUBLISHER_PROPERTY).placementId(extImpTeqblaze.getPlacementId());
        } else if (StringUtils.isNotEmpty(extImpTeqblaze.getEndpointId())) {
            builder.type(NETWORK_PROPERTY).endpointId(extImpTeqblaze.getEndpointId());
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
            final List<BidderBid> bids = extractBids(bidResponse);
            return Result.withValues(bids);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
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
        final Integer markupType = ObjectUtils.defaultIfNull(bid.getMtype(), 0);

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException(
                    "could not define media type for impression: " + bid.getImpid());
        };
    }
}
