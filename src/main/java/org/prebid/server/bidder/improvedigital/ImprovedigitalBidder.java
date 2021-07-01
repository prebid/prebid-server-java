package org.prebid.server.bidder.improvedigital;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.improvedigital.proto.ImprovedigitalBidExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.improvedigital.ExtImpImprovedigital;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * ImproveDigital {@link Bidder} implementation.
 */
public class ImprovedigitalBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpImprovedigital>> IMPROVEDIGITAL_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpImprovedigital>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ImprovedigitalBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        for (Imp imp : request.getImp()) {
            try {
                parseImpExt(imp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                return Result.withErrors(errors);
            }
        }

        return Result.withValues(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .headers(HttpUtil.headers())
                        .payload(request)
                        .body(mapper.encode(request))
                        .build()));
    }

    private ExtImpImprovedigital parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), IMPROVEDIGITAL_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        if (bidResponse.getSeatbid().size() > 1) {
            throw new PreBidException(String.format("Unexpected SeatBid! Must be only one but have: %d",
                    bidResponse.getSeatbid().size()));
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> makeBidderBid(bidRequest, bidResponse, bid))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private BidderBid makeBidderBid(BidRequest bidRequest, BidResponse bidResponse, Bid bid) {
        Bid modifiedBid = bid;
        if (bid.getExt() != null) {
            final ImprovedigitalBidExt improvedigitalBidExt;
            try {
                improvedigitalBidExt = mapper.mapper().treeToValue(bid.getExt(), ImprovedigitalBidExt.class);
            } catch (JsonProcessingException e) {
                throw new PreBidException(e.getMessage(), e);
            }
            // Populate dealId
            String buyingType = improvedigitalBidExt.getImprovedigital().getBuyingType();
            Integer lineItemId = improvedigitalBidExt.getImprovedigital().getLineItemId();
            if (!StringUtils.isBlank(buyingType) && buyingType.matches("(classic|deal)")
                    && lineItemId != null && lineItemId > 0) {
                modifiedBid = bid.toBuilder().dealid(lineItemId.toString()).build();
            }
        }
        return BidderBid.of(modifiedBid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur());
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                }
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
                throw new PreBidException(String.format("Unknown impression type for ID: \"%s\"", impId));
            }
        }
        throw new PreBidException(String.format("Failed to find impression for ID: \"%s\"", impId));
    }
}
