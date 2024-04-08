package org.prebid.server.bidder.zmaticoo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
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
import org.prebid.server.model.UpdateResult;
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
                validateImpExt(imp);
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

    private void validateImpExt(Imp imp) {
        final ExtImpZMaticoo extImpZMaticoo;
        try {
            extImpZMaticoo = mapper.mapper().convertValue(imp.getExt(), ZMATICOO_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
        if (StringUtils.isBlank(extImpZMaticoo.getPubId()) || StringUtils.isBlank(extImpZMaticoo.getZoneId())) {
            throw new PreBidException("imp.ext.pubId or imp.ext.zoneId required");
        }
    }

    private Imp modifyImp(Imp imp) {
        final Native xNative = imp.getXNative();
        if (xNative == null) {
            return imp;
        }

        final UpdateResult<String> nativeRequest = resolveNativeRequest(xNative.getRequest());
        return nativeRequest.isUpdated()
                ? imp.toBuilder()
                .xNative(xNative.toBuilder()
                        .request(nativeRequest.getValue())
                        .build())
                .build()
                : imp;
    }

    private UpdateResult<String> resolveNativeRequest(String nativeRequest) {
        final JsonNode nativeRequestNode;
        try {
            nativeRequestNode = StringUtils.isNotBlank(nativeRequest)
                    ? mapper.mapper().readTree(nativeRequest)
                    : mapper.mapper().createObjectNode();
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }

        if (nativeRequestNode.has("native")) {
            return UpdateResult.unaltered(nativeRequest);
        }

        final String updatedNativeRequest = mapper.mapper().createObjectNode()
                .putPOJO("native", nativeRequestNode)
                .toString();

        return UpdateResult.updated(updatedNativeRequest);
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

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, String currency, List<BidderError> errors) {
        try {
            final BidType bidType = getBidMediaType(bid);
            return BidderBid.of(bid, bidType, currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType getBidMediaType(Bid bid) {
        final int markupType = ObjectUtils.defaultIfNull(bid.getMtype(), 0);
        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException(
                    "unrecognized bid type in response from zmaticoo for bid " + bid.getImpid());
        };
    }

}
