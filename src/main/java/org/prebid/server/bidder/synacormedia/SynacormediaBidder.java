package org.prebid.server.bidder.synacormedia;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.synacormedia.ExtImpSynacormedia;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SynacormediaBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSynacormedia>> SYNACORMEDIA_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpSynacormedia>>() {
            };
    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;

    public SynacormediaBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();

        final List<Imp> validImps = new ArrayList<>();
        ExtImpSynacormedia firstExtImp = null;
        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpSynacormedia extImpSynacormedia = parseExtImp(imp.getExt());
                validImps.add(imp);

                if (firstExtImp == null) {
                    firstExtImp = extImpSynacormedia;
                }
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (validImps.isEmpty()) {
            return Result.of(Collections.emptyList(), errors);
        }

        if (firstExtImp == null || StringUtils.isBlank(firstExtImp.getSeatId())) {
            errors.add(BidderError.badInput("Impression missing seat id"));
            return Result.of(Collections.emptyList(), errors);
        }

        final BidRequest outgoingRequest = bidRequest.toBuilder()
                .imp(validImps)
                .ext(Json.mapper.valueToTree(firstExtImp))
                .build();
        final String body = Json.encode(outgoingRequest);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .headers(HttpUtil.headers())
                        .uri(endpointUrl.replaceAll("\\{\\{Host}}", firstExtImp.getSeatId()))
                        .body(body)
                        .payload(outgoingRequest)
                        .build()),
                errors);
    }

    private static ExtImpSynacormedia parseExtImp(ObjectNode impExt) {
        try {
            return Json.mapper.<ExtPrebid<?, ExtImpSynacormedia>>convertValue(impExt, SYNACORMEDIA_EXT_TYPE_REFERENCE)
                    .getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, httpCall.getRequest().getPayload()), Collections.emptyList());
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse, bidRequest);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getMediaTypeForImp(bid.getImpid(), bidRequest.getImp()),
                        DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    private static BidType getMediaTypeForImp(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    break;
                }
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
                if (imp.getAudio() != null) {
                    return BidType.audio;
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
