package org.prebid.server.bidder.bizzclick;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.bizzclick.ExtImpBizzclick;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class BizzclickBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpBizzclick>> BIZZCLICK_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String URL_SOURCE_ID_MACRO = "{{.SourceId}}";
    private static final String URL_ACCOUNT_ID_MACRO = "{{.AccountID}}";
    private static final String DEFAULT_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public BizzclickBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> imps = request.getImp();
        final ExtImpBizzclick extImpBizzclick;
        try {
            extImpBizzclick = parseImpExt(imps.get(0));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final List<Imp> modifiedImps = imps.stream()
                .map(BizzclickBidder::modifyImp)
                .collect(Collectors.toList());

        return Result.withValue(createHttpRequest(request, modifiedImps, extImpBizzclick));
    }

    private ExtImpBizzclick parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), BIZZCLICK_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("ext.bidder not provided");
        }
    }

    private static Imp modifyImp(Imp imp) {
        return imp.toBuilder().ext(null).build();
    }

    private HttpRequest<BidRequest> createHttpRequest(BidRequest request, List<Imp> imps, ExtImpBizzclick ext) {
        final BidRequest modifiedRequest = request.toBuilder().imp(imps).build();

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .headers(headers(modifiedRequest.getDevice()))
                .uri(buildEndpointUrl(ext))
                .body(mapper.encodeToBytes(modifiedRequest))
                .payload(modifiedRequest)
                .build();
    }

    private static MultiMap headers(Device device) {
        final MultiMap headers = HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
        }

        return headers;
    }

    private String buildEndpointUrl(ExtImpBizzclick ext) {
        return endpointUrl.replace(URL_SOURCE_ID_MACRO, HttpUtil.encodeUrl(ext.getPlacementId()))
                .replace(URL_ACCOUNT_ID_MACRO, HttpUtil.encodeUrl(ext.getAccountId()));
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = parseBidResponse(httpCall.getResponse());
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private BidResponse parseBidResponse(HttpResponse response) {
        try {
            return mapper.decodeValue(response.getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException("Bad server response.");
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid array");
        }

        final SeatBid seatBid = bidResponse.getSeatbid().get(0);
        if (seatBid == null || CollectionUtils.isEmpty(seatBid.getBid())) {
            return Collections.emptyList();
        }

        return seatBid.getBid().stream()
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, resolveBidType(bid.getImpid(), bidRequest.getImp()), DEFAULT_CURRENCY))
                .collect(Collectors.toList());
    }

    private static BidType resolveBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (Objects.equals(impId, imp.getId())) {
                if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
                break;
            }
        }
        return BidType.banner;
    }
}
