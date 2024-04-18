package org.prebid.server.bidder.medianet;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.medianet.model.response.InterestGroupAuctionIntent;
import org.prebid.server.bidder.medianet.model.response.MedianetBidResponse;
import org.prebid.server.bidder.medianet.model.response.MedianetBidResponseExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.CompositeBidderResponse;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.FledgeAuctionConfig;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MedianetBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public MedianetBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        return Result.withValue(BidderUtil.defaultRequest(bidRequest, endpointUrl, mapper));
    }

    /**
     * @deprecated for this bidder in favor of @link{makeBidderResponse} which supports additional response data
     */
    @Override
    @Deprecated(forRemoval = true)
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final MedianetBidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), MedianetBidResponse.class);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bids = extractBids(httpCall.getRequest().getPayload(), bidResponse, errors);

        return Result.of(bids, errors);
    }

    @Override
    public final CompositeBidderResponse makeBidderResponse(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final MedianetBidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), MedianetBidResponse.class);
        } catch (DecodeException e) {
            return CompositeBidderResponse.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bids = extractBids(httpCall.getRequest().getPayload(), bidResponse, errors);
        final List<FledgeAuctionConfig> fledgeAuctionConfigs = extractFledge(bidResponse);

        return CompositeBidderResponse.builder()
                .bids(bids)
                .fledgeAuctionConfigs(fledgeAuctionConfigs)
                .errors(errors)
                .build();
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, MedianetBidResponse bidResponse,
                                               List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        final String currency = bidResponse.getCur();
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, bidRequest.getImp(), currency, errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidType resolveBidType(Bid bid, List<Imp> imps) {
        final Integer markupType = bid.getMtype();
        if (markupType == null) {
            return resolveBidTypeFromImpId(bid.getImpid(), imps);
        }

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException("Unable to fetch mediaType: %s"
                    .formatted(bid.getImpid()));
        };
    }

    private static BidderBid makeBidderBid(Bid bid, List<Imp> imps, String cur, List<BidderError> errors) {
        final BidType bidType;
        try {
            bidType = resolveBidType(bid, imps);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }

        return BidderBid.of(bid, bidType, cur);
    }

    private static BidType resolveBidTypeFromImpId(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (Objects.equals(impId, imp.getId())) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                } else if (imp.getAudio() != null) {
                    return BidType.audio;
                }
            }
        }

        return BidType.banner;
    }

    private static List<FledgeAuctionConfig> extractFledge(MedianetBidResponse bidResponse) {
        return Optional.ofNullable(bidResponse)
                .map(MedianetBidResponse::getExt)
                .map(MedianetBidResponseExt::getIgi)
                .orElse(Collections.emptyList())
                .stream()
                .map(InterestGroupAuctionIntent::getIgs)
                .flatMap(Collection::stream)
                .map(e -> FledgeAuctionConfig.builder().impId(e.getImpId()).config(e.getConfig()).build())
                .toList();
    }
}
