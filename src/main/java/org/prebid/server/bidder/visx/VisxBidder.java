package org.prebid.server.bidder.visx;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.visx.model.VisxBid;
import org.prebid.server.bidder.visx.model.VisxResponse;
import org.prebid.server.bidder.visx.model.VisxSeatBid;
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

public class VisxBidder implements Bidder<BidRequest> {

    private static final String DEFAULT_REQUEST_CURRENCY = "USD";
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private final String endpointUrl;
    private final JacksonMapper mapper;

    public VisxBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        return Result.withValue(makeRequest(request));
    }

    private HttpRequest<BidRequest> makeRequest(BidRequest bidRequest) {
        final BidRequest outgoingRequest = modifyRequest(bidRequest);;
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .body(mapper.encode(outgoingRequest))
                .headers(HttpUtil.headers())
                .payload(outgoingRequest)
                .build();
    }

    private BidRequest modifyRequest(BidRequest bidRequest) {
        if (CollectionUtils.isEmpty(bidRequest.getCur())) {
            return bidRequest.toBuilder()
                    .cur(Collections.singletonList(DEFAULT_REQUEST_CURRENCY))
                    .build();
        }
        return bidRequest;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final VisxResponse visxResponse = mapper.decodeValue(httpCall.getResponse().getBody(), VisxResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), visxResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, VisxResponse visxResponse) {
        if (visxResponse == null || CollectionUtils.isEmpty(visxResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, visxResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, VisxResponse visxResponse) {
        return visxResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(VisxSeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(makeBid(bid, bidRequest.getId()),
                        getBidType(bid.getImpid(), bidRequest.getImp()), DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    private Bid makeBid(VisxBid bid, String id) {
        return Bid.builder()
                .id(id)
                .impid(bid.getImpid())
                .price(bid.getPrice())
                .adm(bid.getAdm())
                .crid(bid.getCrid())
                .dealid(bid.getDealid())
                .h(bid.getH())
                .w(bid.getW())
                .adomain(bid.getAdomain())
                .build();
    }

    private BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                }
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
            }
        }

        throw new PreBidException(String.format("Unknown impression type for ID: \"%s\"", impId));
    }
}
