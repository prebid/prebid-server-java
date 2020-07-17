package org.prebid.server.bidder.yeahmobi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
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
import java.util.Map;
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
        final List<HttpRequest<BidRequest>> result = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpYeahmobi extImpYeahmobi = parseImpExt(imp);
                final Imp processImp = processImp(imp);
                final String url = endpointUrl + "gw-" + extImpYeahmobi.getZoneId() + "-bid.yeahtargeter.com";
                result.add(createSingleRequest(processImp, request, url));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(result, errors);
    }

    private ExtImpYeahmobi parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), YEAHMOBI_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private Imp processImp(Imp imp) {

        final String updateNativeRequest;
        try {
            final JsonNode nativeRequest = imp.getXNative() != null
                    ? mapper.mapper().readValue(imp.getXNative().getRequest(), JsonNode.class)
                    : null;

            final ObjectNode objectNode = mapper.mapper().createObjectNode().set("native", nativeRequest);
            updateNativeRequest = nativeRequest != null && nativeRequest.get("native") == null
                    ? mapper.mapper().writeValueAsString(objectNode)
                    : null;
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        return updateNativeRequest != null
                ? imp.toBuilder().xNative(Native.builder().request(updateNativeRequest).build()).build()
                : imp;
    }

    private HttpRequest<BidRequest> createSingleRequest(Imp imp, BidRequest request, String url) {
        final BidRequest outgoingRequest = request.toBuilder().imp(Collections.singletonList(imp)).build();

        final String body = mapper.encode(outgoingRequest);

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(url)

                .headers(HttpUtil.headers())
                .body(body)
                .payload(outgoingRequest)
                .build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        } else if (statusCode == HttpResponseStatus.BAD_REQUEST.code()) {
            return Result.emptyWithError(BidderError.badInput("Invalid request."));
        } else if (statusCode != HttpResponseStatus.OK.code()) {
            return Result.emptyWithError(BidderError.badServerResponse(String.format("Unexpected HTTP status %s.",
                    statusCode)));
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
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
        BidType bidType = BidType.banner;
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    break;
                } else if (imp.getVideo() != null) {
                    bidType = BidType.video;
                    break;
                } else if (imp.getXNative() != null) {
                    bidType = BidType.xNative;
                    break;
                }
            }
        }
        return bidType;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
