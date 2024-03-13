package org.prebid.server.bidder.zmaticoo;

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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.zmaticoo.ExtImpZMaticoo;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ZMaticooBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpZMaticoo>> ZMATICOO_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ZMaticooBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> bidderErrors = new ArrayList<>();
        final List<Imp> modifiedImps = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                parseImpExt(imp);
                modifiedImps.add(modifyImp(imp));
            } catch (PreBidException e) {
                bidderErrors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (CollectionUtils.isNotEmpty(bidderErrors)) {
            return Result.withErrors(bidderErrors);
        }

        final BidRequest modifiedRequest = request.toBuilder().imp(modifiedImps).build();
        return Result.withValue(makeHttpRequest(modifiedRequest));
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest modifiedRequest) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .impIds(BidderUtil.impIds(modifiedRequest))
                .payload(modifiedRequest)
                .body(mapper.encodeToBytes(modifiedRequest))
                .build();
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

    private ExtImpZMaticoo parseImpExt(Imp imp) {
        final ExtImpZMaticoo extImpZMaticoo;
        try {
            extImpZMaticoo = mapper.mapper().convertValue(imp.getExt(), ZMATICOO_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
        if (StringUtils.isBlank(extImpZMaticoo.getPubId()) || StringUtils.isBlank(extImpZMaticoo.getZoneId())) {
            throw new PreBidException("imp.ext.pubId or imp.ext.zoneId required");
        }
        return extImpZMaticoo;
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (PreBidException | DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
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
