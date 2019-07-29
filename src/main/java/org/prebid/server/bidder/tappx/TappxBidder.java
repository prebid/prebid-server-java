package org.prebid.server.bidder.tappx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.tappx.ExtImpTappx;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class TappxBidder implements Bidder<BidRequest> {

    private static final String VERSION = "1.0";

    private static final TypeReference<ExtPrebid<?, ExtImpTappx>> TAPX_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpTappx>>() {
            };
    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;

    public TappxBidder(String endpointUrl) {
        this.endpointUrl = Objects.requireNonNull(endpointUrl);
    }

    /**
     * Makes the HTTP requests which should be made to fetch bids.
     * <p>
     * Creates POST http request with all parameters in url and headers with encoded request in body.
     */
    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final ExtImpTappx extImpTappx;
        final String url;
        try {
            extImpTappx = parseBidRequestToExtImpTappx(request);
            url = buildEndpointUrl(extImpTappx, request.getTest());
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badInput(e.getMessage()));
        }

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(url)
                        .body(Json.encode(request))
                        .headers(HttpUtil.headers())
                        .payload(request)
                        .build()),
                Collections.emptyList());
    }

    /**
     * Retrieves first {@link ExtImpTappx} from {@link Imp}.
     */
    private static ExtImpTappx parseBidRequestToExtImpTappx(BidRequest request) {
        try {
            return Json.mapper.<ExtPrebid<?, ExtImpTappx>>convertValue(
                    request.getImp().get(0).getExt(), TAPX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    /**
     * Builds endpoint url based on adapter-specific pub settings from imp.ext.
     */
    private String buildEndpointUrl(ExtImpTappx extImpTappx, Integer test) {
        final String host = extImpTappx.getHost();
        if (StringUtils.isBlank(host)) {
            throw new PreBidException("Tappx host undefined");
        }

        final String endpoint = extImpTappx.getEndpoint();
        if (StringUtils.isBlank(endpoint)) {
            throw new PreBidException("Tappx endpoint undefined");
        }

        final String tappxkey = extImpTappx.getTappxkey();
        if (StringUtils.isBlank(tappxkey)) {
            throw new PreBidException("Tappx tappxkey undefined");
        }

        String url = String.format("%s%s%s?tappxkey=%s", endpointUrl, host, endpoint, tappxkey);
        if (test != null && test == 0) {
            int t = (int) System.nanoTime();
            url += "&ts=" + t;
        }

        url += "&v=" + VERSION;

        try {
            HttpUtil.validateUrl(url);
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Not valid url: " + url, e);
        }

        return url;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
            }
        }
        return BidType.banner;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}

