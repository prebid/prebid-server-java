package org.prebid.server.bidder.afront;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
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
import org.prebid.server.proto.openrtb.ext.request.afront.ExtImpAfront;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AfrontBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAfront>> TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final String ACCOUNT_ID_MACRO = "{{AccountId}}";
    private static final String SOURCE_ID_MACRO = "{{SourceId}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AfrontBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final ExtImpAfront extImp;
        try {
            extImp = parseImpExt(request.getImp().getFirst());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final String resolvedEndpoint = resolveEndpoint(extImp);
        final BidRequest outgoingRequest = modifyRequest(request);
        final HttpRequest<BidRequest> httpRequest =
                BidderUtil.defaultRequest(outgoingRequest, makeHeaders(request.getDevice()), resolvedEndpoint, mapper);

        return Result.withValue(httpRequest);
    }

    private ExtImpAfront parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("ext.bidder not provided");
        }
    }

    private String resolveEndpoint(ExtImpAfront extImp) {
        return endpointUrl
                .replace(ACCOUNT_ID_MACRO, HttpUtil.encodeUrl(extImp.getAccountId()))
                .replace(SOURCE_ID_MACRO, HttpUtil.encodeUrl(extImp.getSourceId()));
    }

    private static BidRequest modifyRequest(BidRequest request) {
        final List<Imp> modifiedImps = request.getImp().stream()
                .map(imp -> imp.toBuilder().ext(null).build())
                .toList();

        return request.toBuilder()
                .imp(modifiedImps)
                .build();
    }

    private static MultiMap makeHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidRequest, bidResponse));
        } catch (DecodeException e) {
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
            if (imp.getVideo() != null) {
                return BidType.video;
            } else if (imp.getXNative() != null) {
                return BidType.xNative;
            }
        }
        return BidType.banner;
    }
}
