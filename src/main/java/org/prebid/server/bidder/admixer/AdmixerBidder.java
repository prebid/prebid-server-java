package org.prebid.server.bidder.admixer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.admixer.ExtImpAdmixer;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Admixer {@link Bidder} implementation.
 */
public class AdmixerBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdmixer>> ADMIXER_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdmixer>>() {
            };

    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdmixerBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();

        if (CollectionUtils.isEmpty(request.getImp())) {
            errors.add(BidderError.badInput("No valid impressions in the bid request"));
            return Result.of(Collections.emptyList(), errors);
        }

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpAdmixer extImp = parseImpExt(imp);
                final Imp updatedImp = processImp(imp, extImp);
                validImps.add(updatedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        final BidRequest outgoingRequest = request.toBuilder().imp(validImps).build();
        final String body = mapper.encode(outgoingRequest);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .headers(HttpUtil.headers())
                        .payload(outgoingRequest)
                        .body(body)
                        .build()),
                errors);
    }

    private ExtImpAdmixer parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADMIXER_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private Imp processImp(Imp imp, ExtImpAdmixer extImpAdmixer) {
        if (extImpAdmixer.getZone().length() != 36) {
            throw new PreBidException("ZoneId must be UUID/GUID");
        }

        return imp.toBuilder()
                .tagid(extImpAdmixer.getZone())
                .bidfloor(BigDecimal.valueOf(extImpAdmixer.getCustomFloor()))
                .ext(makeImpExt(extImpAdmixer.getCustomParams()))
                .build();
    }

    private ObjectNode makeImpExt(Map<String, JsonNode> customParams) {
        return customParams != null
                ? mapper.mapper().valueToTree(ExtImpAdmixer.of(null, null, customParams))
                : null;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.empty();
        }

        final BidResponse bidResponse;
        try {
            bidResponse = decodeBodyToBidResponse(httpCall);
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }

        if (CollectionUtils.isEmpty(bidResponse.getSeatbid())
                || CollectionUtils.isEmpty(bidResponse.getSeatbid().get(0).getBid())) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }

        final List<BidderBid> bidderBids = bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
        return Result.of(bidderBids, Collections.emptyList());
    }

    private BidResponse decodeBodyToBidResponse(HttpCall<BidRequest> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                } else if (imp.getAudio() != null) {
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
