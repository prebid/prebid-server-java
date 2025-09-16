package org.prebid.server.bidder.stroeercore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.stroeercore.model.StroeerCoreBid;
import org.prebid.server.bidder.stroeercore.model.StroeerCoreBidResponse;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.stroeercore.ExtImpStroeerCore;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class StroeerCoreBidder implements Bidder<BidRequest> {

    private static final String BIDDER_CURRENCY = "EUR";
    private static final TypeReference<ExtPrebid<?, ExtImpStroeerCore>> STROEER_CORE_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public StroeerCoreBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(endpointUrl);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<Imp> modifiedImps = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpStroeerCore impExt = parseImpExt(imp);
                modifiedImps.add(imp.toBuilder().tagid(impExt.getSlotId()).build());
            } catch (PreBidException e) {
                errors.add(BidderError.badInput("%s. Ignore imp id = %s.".formatted(e.getMessage(), imp.getId())));
            }
        }

        if (modifiedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest outgoingRequest = bidRequest.toBuilder().imp(modifiedImps).build();
        return Result.withValue(BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper));
    }

    private ExtImpStroeerCore parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), STROEER_CORE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final String body = httpCall.getResponse().getBody();
            final List<BidderError> errors = new ArrayList<>();
            final StroeerCoreBidResponse bidResponse = mapper.decodeValue(body, StroeerCoreBidResponse.class);
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(StroeerCoreBidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getBids())) {
            return Collections.emptyList();
        }

        return bidResponse.getBids().stream()
                .filter(Objects::nonNull)
                .map(stroeerCoreBid -> toBidderBid(stroeerCoreBid, errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid toBidderBid(StroeerCoreBid stroeercoreBid, List<BidderError> errors) {
        final BidType bidType = getBidType(stroeercoreBid.getMtype());
        if (bidType == null) {
            errors.add(BidderError.badServerResponse(
                    "Bid media type error: unable to determine media type for bid with id \"%s\""
                            .formatted(stroeercoreBid.getBidId())));
            return null;
        }

        final ObjectNode bidExt = stroeercoreBid.getDsa() != null
                ? mapper.mapper().createObjectNode().set("dsa", stroeercoreBid.getDsa())
                : null;

        return BidderBid.of(
                Bid.builder()
                        .id(stroeercoreBid.getId())
                        .impid(stroeercoreBid.getBidId())
                        .w(stroeercoreBid.getWidth())
                        .h(stroeercoreBid.getHeight())
                        .price(stroeercoreBid.getCpm())
                        .adm(stroeercoreBid.getAdMarkup())
                        .crid(stroeercoreBid.getCreativeId())
                        .adomain(stroeercoreBid.getAdomain())
                        .mtype(bidType.ordinal() + 1)
                        .ext(bidExt)
                        .build(),
                bidType,
                BIDDER_CURRENCY);
    }

    private static BidType getBidType(String mtype) {
        return switch (mtype) {
            case "banner" -> BidType.banner;
            case "video" -> BidType.video;
            default -> null;
        };
    }
}
