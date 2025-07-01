package org.prebid.server.bidder.flatads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
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
import org.prebid.server.proto.openrtb.ext.request.flatads.ExtImpFlatads;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FlatadsBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpFlatads>> FLATADS_EXT_TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final String PUBLISHER_ID_MACRO = "{{PublisherID}}";
    private static final String TOKEN_ID_MACRO = "{{TokenID}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public FlatadsBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpFlatads extImpFlatads = parseImpExt(imp);
                final String resolvedEndpoint = resolveEndpoint(extImpFlatads);
                final BidRequest outgoingRequest = request.toBuilder()
                        .imp(Collections.singletonList(imp))
                        .build();
                httpRequests.add(makeHttpRequest(outgoingRequest, resolvedEndpoint));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpFlatads parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), FLATADS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to deserialize Flatads extension: " + e.getMessage());
        }
    }

    private String resolveEndpoint(ExtImpFlatads extImp) {
        return endpointUrl
                .replace(PUBLISHER_ID_MACRO, HttpUtil.encodeUrl(StringUtils.defaultString(extImp.getPublisherId())))
                .replace(TOKEN_ID_MACRO, HttpUtil.encodeUrl(StringUtils.defaultString(extImp.getToken())));
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, String endpoint) {
        return BidderUtil.defaultRequest(request, makeHeaders(request.getDevice()), endpoint, mapper);
    }

    private static MultiMap makeHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers();

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidRequest, bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        final Map<String, Imp> imps = bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, Function.identity()));

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), imps), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(String impId, Map<String, Imp> imps) {
        final Imp imp = imps.get(impId);
        if (imp != null) {
            if (imp.getBanner() != null) {
                return BidType.banner;
            } else if (imp.getVideo() != null) {
                return BidType.video;
            } else if (imp.getXNative() != null) {
                return BidType.xNative;
            }
        }

        throw new PreBidException("The impression with ID %s is not present into the request".formatted(impId));
    }
}
