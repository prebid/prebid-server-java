package org.prebid.server.bidder.roulax;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.prebid.server.proto.openrtb.ext.request.roulax.ExtImpRoulax;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RoulaxBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    private static final String PUBLISHER_PATH_MACRO = "{{PublisherID}}";
    private static final String ACCOUNT_ID_MACRO = "{{AccountID}}";

    private static final TypeReference<ExtPrebid<?, ExtImpRoulax>> ROULAX_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    public RoulaxBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        try {
            final ExtImpRoulax extImpRoulax = parseImpExt(bidRequest.getImp().getFirst());
            return Result.withValue(BidderUtil.defaultRequest(bidRequest, resolveEndpoint(extImpRoulax), mapper));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
    }

    private ExtImpRoulax parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ROULAX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to deserialize Roulax extension: " + e.getMessage());
        }
    }

    private String resolveEndpoint(ExtImpRoulax extImpRoulax) {
        return endpointUrl
                .replace(PUBLISHER_PATH_MACRO, StringUtils.defaultString(extImpRoulax.getPublisherPath()).trim())
                .replace(ACCOUNT_ID_MACRO, StringUtils.defaultString(extImpRoulax.getPid()).trim());
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBid(Bid bid, String currency, List<BidderError> errors) {
        try {
            return BidderBid.of(bid, getBidType(bid), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType getBidType(Bid bid) {
        final Integer markupType = bid.getMtype();
        if (markupType == null) {
            throw new PreBidException("Missing MType for bid: " + bid.getId());
        }
        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException(
                    "Unable to fetch mediaType in impID: %s, mType: %d".formatted(bid.getImpid(), bid.getMtype()));
        };
    }
}
