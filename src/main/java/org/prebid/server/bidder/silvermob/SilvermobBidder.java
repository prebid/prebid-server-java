package org.prebid.server.bidder.silvermob;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.silvermob.ExtImpSilvermob;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SilvermobBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSilvermob>> SILVERMOB_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpSilvermob>>() {
            };

    private static final String URL_HOST_MACRO = "{{Host}}";
    private static final String URL_ZONE_ID_MACRO = "{{ZoneID}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SilvermobBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                requests.add(createRequestForImp(imp, request));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private HttpRequest<BidRequest> createRequestForImp(Imp imp, BidRequest request) {
        final ExtImpSilvermob extImp = parseImpExt(imp);

        final BidRequest outgoingRequest = request.toBuilder()
                .imp(Collections.singletonList(imp))
                .build();
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(resolveEndpoint(extImp))
                .headers(resolveHeaders(request.getDevice()))
                .payload(outgoingRequest)
                .body(mapper.encodeToBytes(outgoingRequest))
                .build();
    }

    private ExtImpSilvermob parseImpExt(Imp imp) {
        final ExtImpSilvermob extImp;
        try {
            extImp = mapper.mapper().convertValue(imp.getExt(), SILVERMOB_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("error unmarshalling imp.ext.bidder: %s", e.getMessage()));
        }
        if (StringUtils.isBlank(extImp.getHost())) {
            throw new PreBidException("host is a required silvermob ext.imp param");
        }

        if (StringUtils.isBlank(extImp.getZoneId())) {
            throw new PreBidException("zoneId is a required silvermob ext.imp param");
        }
        return extImp;
    }

    private String resolveEndpoint(ExtImpSilvermob extImp) {
        return endpointUrl
                .replace(URL_HOST_MACRO, extImp.getHost())
                .replace(URL_ZONE_ID_MACRO, HttpUtil.encodeUrl(extImp.getZoneId()));
    }

    private static MultiMap resolveHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            return Result.of(extractBids(httpCall), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(HttpCall<BidRequest> httpCall) {
        final BidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(String.format("Error unmarshalling server Response: %s", e.getMessage()));
        }
        if (bidResponse == null) {
            throw new PreBidException("Response in not present");
        }
        if (CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid array");
        }
        return bidsFromResponse(bidResponse, httpCall.getRequest().getPayload());
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
            }
        }
        return BidType.banner;
    }
}
