package org.prebid.server.bidder.screencore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.MultiMap;
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
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.screencore.ScreencoreImpExt;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ScreencoreBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ScreencoreImpExt>> TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final String ACCOUNT_ID_MACRO = "{{AccountId}}";
    private static final String SOURCE_ID_MACRO = "{{SourceId}}";
    private static final String X_OPENRTB_VERSION = "2.5";
    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ScreencoreBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final ScreencoreImpExt impExt;
        final Imp firstImp = request.getImp().get(0);
        try {
            impExt = parseImpExt(firstImp);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final BidRequest bidRequest = cleanUpFirstImpExt(request);
        final HttpRequest<BidRequest> httpRequest = makeHttpRequest(request, impExt, bidRequest);
        return Result.withValue(httpRequest);
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request,
                                                    ScreencoreImpExt impExt,
                                                    BidRequest bidRequest) {

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(resolveEndpoint(impExt))
                .headers(makeHeaders(request))
                .impIds(BidderUtil.impIds(bidRequest))
                .body(mapper.encodeToBytes(bidRequest))
                .payload(bidRequest)
                .build();
    }

    private ScreencoreImpExt parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static BidRequest cleanUpFirstImpExt(BidRequest request) {
        final List<Imp> imps = new ArrayList<>(request.getImp());
        imps.set(0, request.getImp().get(0).toBuilder().ext(null).build());
        return request.toBuilder().imp(imps).build();
    }

    private String resolveEndpoint(ScreencoreImpExt impExt) {
        return endpointUrl
                .replace(ACCOUNT_ID_MACRO, HttpUtil.encodeUrl(impExt.getAccountId()))
                .replace(SOURCE_ID_MACRO, HttpUtil.encodeUrl(impExt.getPlacementId()));
    }

    private static MultiMap makeHeaders(BidRequest bidRequest) {
        final Device device = bidRequest.getDevice();
        final MultiMap headers = HttpUtil.headers();

        headers.set(HttpUtil.X_OPENRTB_VERSION_HEADER, X_OPENRTB_VERSION);
        HttpUtil.addHeaderIfValueIsNotEmpty(
                headers,
                HttpUtil.USER_AGENT_HEADER,
                ObjectUtil.getIfNotNull(device, Device::getUa));
        HttpUtil.addHeaderIfValueIsNotEmpty(
                headers,
                HttpUtil.X_FORWARDED_FOR_HEADER,
                ObjectUtil.getIfNotNull(device, Device::getIpv6));
        HttpUtil.addHeaderIfValueIsNotEmpty(
                headers,
                HttpUtil.X_FORWARDED_FOR_HEADER,
                ObjectUtil.getIfNotNull(device, Device::getIp));

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse("Bad Server Response"));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid array");
        }

        return bidResponse.getSeatbid()
                .stream()
                .flatMap(seatBid -> Optional.ofNullable(seatBid.getBid()).orElse(List.of()).stream())
                .map(bid -> BidderBid.of(bid, getBidMediaType(bid), bidResponse.getCur()))
                .toList();
    }

    private static BidType getBidMediaType(Bid bid) {
        final Integer markupType = bid.getMtype();
        if (markupType == null) {
            throw new PreBidException("Missing MType for bid: " + bid.getId());
        }

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException(
                    "Unable to fetch mediaType " + bid.getMtype() + " in multi-format: " + bid.getImpid());
        };
    }

}
