package org.prebid.server.bidder.sspbc;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
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

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

public class SspbcBidder implements Bidder<SspbcRequest> {

    private static final String ADAPTER_VERSION = "6.0";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SspbcBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<SspbcRequest>>> makeHttpRequests(BidRequest request) {
        try {
            final SspbcRequest outgoingRequest = SspbcRequest.of(request);
            final String uri = updateUrl(getUri(endpointUrl));
            final HttpRequest<SspbcRequest> httpRequest = createHttpRequest(outgoingRequest, uri, mapper);
            return Result.withValue(httpRequest);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
    }

    public HttpRequest<SspbcRequest> createHttpRequest(SspbcRequest sspbcRequest,
                                                       String endpointUrl,
                                                       JacksonMapper mapper) {
        return HttpRequest.<SspbcRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .impIds(BidderUtil.impIds(sspbcRequest.getBidRequest()))
                .body(mapper.encodeToBytes(sspbcRequest))
                .payload(sspbcRequest)
                .build();

    }

    private static URIBuilder getUri(String endpointUrl) {
        final URIBuilder uri;
        try {
            uri = new URIBuilder(endpointUrl);
        } catch (URISyntaxException e) {
            throw new PreBidException("Malformed URL: %s.".formatted(endpointUrl));
        }
        return uri;
    }

    private String updateUrl(URIBuilder uriBuilder) {
        return uriBuilder
                .addParameter("bdver", ADAPTER_VERSION)
                .toString();
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
                .map(seatBid -> CollectionUtils.emptyIfNull(seatBid.getBid())
                        .stream()
                        .filter(Objects::nonNull)
                        .map(bid -> toBidderBid(bid, bidResponse.getCur())
                    ))
                .flatMap(UnaryOperator.identity())
                .toList();
    }

    private BidderBid toBidderBid(Bid bid, String currency) {
        if (StringUtils.isEmpty(bid.getAdm())) {
            throw new PreBidException("Bid format is not supported");
        }

        return BidderBid.of(bid, getBidType(bid), currency);
    }

    private BidType getBidType(Bid bid) {
        return switch (bid.getMtype()) {
            case null -> throw new PreBidException("Bid mtype is required");
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException("unsupported MType: %s.".formatted(bid.getMtype()));
        };
    }

}
