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
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
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
    private static final String HOST_MACRO = "{{Host}}";
    private static final String HOST_PATTERN = "gw-%s-bid.yeahtargeter.com";
    private static final String NATIVE = "native";

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
            return Result.withErrors(errors);
        }

        final BidRequest modifiedRequest = request.toBuilder().imp(modifiedImps).build();

        final HttpRequest<BidRequest> httpRequest = makeHttpRequest(modifiedRequest, extImp.getZoneId());
        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private ExtImpYeahmobi parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp modifyImp(Imp imp) {
        final Native impNative = imp.getXNative();
        final String resolvedNativeRequest = impNative != null
                ? resolveNativeRequest(impNative.getRequest())
                : null;

        return resolvedNativeRequest != null
                ? imp.toBuilder()
                .xNative(impNative.toBuilder().request(resolvedNativeRequest).build())
                .build()
                : imp;
    }

    private String resolveNativeRequest(String nativeRequest) {
        if (nativeRequest == null) {
            return null;
        }

        try {
            final JsonNode nativePayload = mapper.mapper().readValue(nativeRequest, JsonNode.class);

            if (nativeRequest.contains(NATIVE)) {
                return nativeRequest;
            }

            final ObjectNode objectNode = mapper.mapper().createObjectNode().set(NATIVE, nativePayload);
            return mapper.mapper().writeValueAsString(objectNode);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, String zoneId) {
        return BidderUtil.defaultRequest(
                request,
                endpointUrl.replace(HOST_MACRO, HOST_PATTERN.formatted(zoneId)),
                mapper);
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

        final Map<String, Imp> impMap = bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, Function.identity()));

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.builder()
                        .bid(bid)
                        .type(getBidType(bid.getImpid(), impMap))
                        .bidCurrency(bidResponse.getCur())
                        .videoInfo(videoInfo(parseBidExt(bid.getExt())))
                        .build())
                .toList();
    }

    private static BidType getBidType(String impId, Map<String, Imp> impIdToImp) {
        if (impId == null) {
            return BidType.banner;
        }

        final Imp imp = impIdToImp.get(impId);
        if (imp.getBanner() != null) {
            return BidType.banner;
        } else if (imp.getVideo() != null) {
            return BidType.video;
        } else if (imp.getXNative() != null) {
            return BidType.xNative;
        } else {
            return BidType.banner;
        }
    }

    private ExtBidPrebid parseBidExt(ObjectNode bidExt) {
        try {
            return mapper.mapper().treeToValue(bidExt, ExtBidPrebid.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("bid.ext json unmarshal error");
        }
    }

    private ExtBidPrebidVideo videoInfo(ExtBidPrebid extBidPrebid) {
        return Optional.ofNullable(extBidPrebid)
                .map(ExtBidPrebid::getVideo)
                .map(ExtBidPrebidVideo::getDuration)
                .map(duration -> ExtBidPrebidVideo.of(duration, null))
                .orElse(null);
    }
}
