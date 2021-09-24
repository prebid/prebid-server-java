package org.prebid.server.bidder.admixer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
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

public class AdmixerBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdmixer>> ADMIXER_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdmixer>>() {
            };

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
        final ExtImpAdmixer extImpAdmixer;
        try {
            extImpAdmixer = mapper.mapper().convertValue(imp.getExt(), ADMIXER_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("Wrong Admixer bidder ext in imp with id : %s", imp.getId()));
        }
        final String zoneId = extImpAdmixer.getZone();

        if (StringUtils.length(zoneId) < 32 || StringUtils.length(zoneId) > 36) {
            throw new PreBidException("ZoneId must be UUID/GUID");
        }

        return extImpAdmixer;
    }

    private Imp processImp(Imp imp, ExtImpAdmixer extImpAdmixer) {
        return imp.toBuilder()
                .tagid(extImpAdmixer.getZone())
                .bidfloor(resolveBidFloor(extImpAdmixer.getCustomFloor(), imp.getBidfloor()))
                .ext(makeImpExt(extImpAdmixer.getCustomParams()))
                .build();
    }

    private static BigDecimal resolveBidFloor(BigDecimal customBidFloor, BigDecimal bidFloor) {
        final BigDecimal resolvedCustomBidFloor = isValidBidFloor(customBidFloor) ? customBidFloor : null;
        final BigDecimal resolvedBidFloor = isValidBidFloor(bidFloor) ? bidFloor : null;

        return ObjectUtils.defaultIfNull(resolvedBidFloor, resolvedCustomBidFloor);
    }

    private static boolean isValidBidFloor(BigDecimal bidFloor) {
        return bidFloor != null && bidFloor.compareTo(BigDecimal.ZERO) > 0;
    }

    private ObjectNode makeImpExt(Map<String, JsonNode> customParams) {
        return customParams != null
                ? mapper.mapper().valueToTree(ExtImpAdmixer.of(null, null, customParams))
                : null;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = decodeBodyToBidResponse(httpCall);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        if (bidResponse == null
                || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                || CollectionUtils.isEmpty(bidResponse.getSeatbid().get(0).getBid())) {
            return Result.empty();
        }

        final List<BidderBid> bidderBids = bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());

        return Result.withValues(bidderBids);
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
}
