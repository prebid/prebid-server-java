package org.prebid.server.bidder.sspbc;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
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

public class SspbcBidder implements Bidder<BidRequest> {

    private static final String ADAPTER_VERSION = "6.0";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SspbcBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrlSyntax(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        try {
            return Result.withValue(createRequest(request));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request) {
        return BidderUtil.defaultRequest(request, updateUrl(getUri(endpointUrl)), mapper);
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
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, httpCall.getRequest().getPayload()));
        } catch (PreBidException | DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(seatBid -> CollectionUtils.emptyIfNull(seatBid.getBid())
                        .stream()
                        .filter(Objects::nonNull)
                        .map(bid -> toBidderBid(bid, seatBid.getSeat(), bidResponse.getCur())
                    ))
                .flatMap(UnaryOperator.identity())
                .toList();
    }

    private BidderBid toBidderBid(Bid bid, String seat, String currency) {
        if (StringUtils.isEmpty(bid.getAdm())) {
            throw new PreBidException("Bid format is not supported");
        }

        return BidderBid.of(bid, getBidType(bid), currency);
    }

    private BidType getBidType(Bid bid) {
        switch (bid.getMtype()) {
            case 1:
                return BidType.banner;
            case 2:
                return BidType.video;
            case 3:
                return BidType.audio;
            case 4:
                return BidType.xNative;
            default:
                throw new PreBidException("Bid type not supported: %s.".formatted(bid.getMtype()));
        }
    }

}
