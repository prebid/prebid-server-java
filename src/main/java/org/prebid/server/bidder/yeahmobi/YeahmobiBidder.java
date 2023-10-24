package org.prebid.server.bidder.yeahmobi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.yeahmobi.ExtImpYeahmobi;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class YeahmobiBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpYeahmobi>> EXT_TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final String HOST_PLACEHOLDER = "{{Host}}";
    private static final String HOST_PATTERN = "gw-%s-bid.yeahtargeter.com";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public YeahmobiBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> modifiedImps = new ArrayList<>();

        ExtImpYeahmobi extImp = null;
        for (Imp imp : request.getImp()) {
            try {
                extImp = parseImpExt(imp);
                modifiedImps.add(modifyImp(imp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (extImp == null) {
            return Result.withError(BidderError.badInput("Invalid ExtImpYeahmobi value"));
        }

        final BidRequest modifiedRequest = request.toBuilder().imp(modifiedImps).build();

        final HttpRequest<BidRequest> httpRequest = makeHttpRequest(modifiedRequest, extImp.getZoneId());
        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, String zoneId) {
        final String host = HOST_PATTERN.formatted(zoneId);
        final String uri = endpointUrl.replace(HOST_PLACEHOLDER, host);

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(uri)
                .impIds(BidderUtil.impIds(request))
                .headers(HttpUtil.headers())
                .payload(request)
                .body(mapper.encodeToBytes(request))
                .build();
    }

    private ExtImpYeahmobi parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("Impression id=%s, has invalid Ext", imp.getId()));
        }
    }

    private Imp modifyImp(Imp imp) {
        final Native impNative = imp.getXNative();
        return Optional.ofNullable(impNative)
                .map(xNative -> resolveNativeRequest(xNative.getRequest()))
                .map(nativeRequest -> imp.toBuilder().xNative(
                        impNative.toBuilder().request(nativeRequest).build())
                        .build())
                .orElse(imp);
    }

    private String resolveNativeRequest(String nativeRequest) {
        try {
            final JsonNode nativePayload = nativeRequest != null
                    ? mapper.mapper().readValue(nativeRequest, JsonNode.class)
                    : mapper.mapper().createObjectNode();
            final ObjectNode objectNode = mapper.mapper().createObjectNode().set("native", nativePayload);
            return mapper.mapper().writeValueAsString(objectNode);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        final Map<String, Imp> impMap = bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, Function.identity()));
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid, impMap), bidResponse.getCur()))
                .toList();
    }

    protected BidType getBidType(Bid bid, Map<String, Imp> impMap) {
        return Optional.ofNullable(impMap.get(bid.getImpid()))
                .map(imp -> {
                    if (imp.getBanner() != null) {
                        return BidType.banner;
                    } else if (imp.getVideo() != null) {
                        return BidType.video;
                    } else if (imp.getXNative() != null) {
                        return BidType.xNative;
                    } else {
                        return BidType.banner;
                    }
                })
                .orElse(BidType.banner);

    }
}
