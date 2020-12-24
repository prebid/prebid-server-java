package org.prebid.server.bidder.yeahmobi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
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
import org.prebid.server.proto.openrtb.ext.request.yeahmobi.ExtImpYeahmobi;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class YeahmobiBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpYeahmobi>> YEAHMOBI_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpYeahmobi>>() {
            };

    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public YeahmobiBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();

        ExtImpYeahmobi extImpYeahmobi = null;
        for (Imp imp : request.getImp()) {
            try {
                extImpYeahmobi = extImpYeahmobi == null ? parseImpExt(imp) : extImpYeahmobi;
                final Imp processImp = processImp(imp);
                validImps.add(processImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (extImpYeahmobi == null) {
            return Result.withError(BidderError.badInput("Invalid ExtImpYeahmobi value"));
        }

        final String host = String.format("gw-%s-bid.yeahtargeter.com", HttpUtil.encodeUrl(extImpYeahmobi.getZoneId()));
        final String url = endpointUrl.replace("{{Host}}", host);

        final BidRequest outgoingRequest = request.toBuilder().imp(validImps).build();

        final String body = mapper.encode(outgoingRequest);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(url)
                        .headers(HttpUtil.headers())
                        .payload(outgoingRequest)
                        .body(body)
                        .build()),
                errors);
    }

    private ExtImpYeahmobi parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), YEAHMOBI_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    @SneakyThrows
    private Imp processImp(Imp imp) {
        final Native xNative = imp.getXNative();

        if (xNative != null) {
            final JsonNode nativeRequest = xNative.getRequest() != null
                    ? mapper.mapper().readValue(xNative.getRequest(), JsonNode.class)
                    : null;

            final String newNativeRequest;
            final ObjectNode objectNode = mapper.mapper().createObjectNode().set("native", nativeRequest);
            newNativeRequest = nativeRequest == null || nativeRequest.get("native") == null
                    ? mapper.mapper().writeValueAsString(objectNode)
                    : null;

            return newNativeRequest != null
                    ? imp.toBuilder().xNative(Native.builder().request(newNativeRequest).build()).build()
                    : imp;
        }
        return imp;
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    protected BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
            }
        }
        return BidType.banner;
    }
}
