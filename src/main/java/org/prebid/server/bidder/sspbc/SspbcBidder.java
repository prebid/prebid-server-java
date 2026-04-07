package org.prebid.server.bidder.sspbc;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.UriTemplate;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SspbcBidder implements Bidder<SspbcRequest> {

    private static final String ADAPTER_VERSION = "6.0";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SspbcBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = resolveEndpointUrl(endpointUrl);
        this.mapper = Objects.requireNonNull(mapper);
    }

    private static String resolveEndpointUrl(String base) {
        return UriTemplate.of(base).toBuilder().queryParam("bdver", ADAPTER_VERSION).build();
    }

    @Override
    public Result<List<HttpRequest<SspbcRequest>>> makeHttpRequests(BidRequest request) {
        return Result.withValue(createHttpRequest(request));
    }

    private HttpRequest<SspbcRequest> createHttpRequest(BidRequest request) {
        final SspbcRequest outgoingRequest = SspbcRequest.of(request);
        return HttpRequest.<SspbcRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .impIds(BidderUtil.impIds(outgoingRequest.getBidRequest()))
                .body(mapper.encodeToBytes(outgoingRequest))
                .payload(outgoingRequest)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<SspbcRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (PreBidException | DecodeException e) {
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

    private static BidType getBidType(Bid bid) {
        return switch (bid.getMtype()) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;
            case null -> throw new PreBidException("Bid mtype is required");
            default -> throw new PreBidException("unsupported MType: %s.".formatted(bid.getMtype()));
        };
    }
}
