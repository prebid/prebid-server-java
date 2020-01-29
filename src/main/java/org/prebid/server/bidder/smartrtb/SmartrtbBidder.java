package org.prebid.server.bidder.smartrtb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.smartrtb.ExtImpSmartrtb;
import org.prebid.server.proto.openrtb.ext.request.smartrtb.ExtRequestSmartrtb;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SmartRTB {@link Bidder} implementation.
 */

public class SmartrtbBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSmartrtb>> SMARTRTB_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpSmartrtb>>() {
            };

    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;

    public SmartrtbBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();
        String pubId = null;
        String zoneId = null;
        Boolean forceBid = null;

        for (Imp imp : request.getImp()) {
            try {
                final Imp validImp = validateImp(imp);
                final ExtImpSmartrtb extImp = parseImpExt(imp);

                if (StringUtils.isEmpty(pubId) && StringUtils.isNoneEmpty(extImp.getPubId())) {
                    pubId = extImp.getPubId();
                }

                zoneId = extImp.getZoneId();
                final Imp updatedImp = StringUtils.isNotEmpty(zoneId)
                        ? validImp.toBuilder().tagid(zoneId).build()
                        : imp;
                validImps.add(updatedImp);

            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (StringUtils.isEmpty(pubId)) {
            errors.add(BidderError.badInput("Cannot infer publisher ID from bid ext"));
            return Result.of(null, errors);
        } else {
            ExtRequestSmartrtb.of(pubId, zoneId, forceBid);
        }

        final BidRequest outgoingRequest = request.toBuilder().imp(validImps).build();
        final String body = Json.encode(outgoingRequest);
        final String requestUrl = endpointUrl + pubId;
        final MultiMap headers = resolveHeaders(request.getDevice());

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(requestUrl)
                        .headers(headers)
                        .payload(outgoingRequest)
                        .body(body)
                        .build()),
                errors);
    }

    private Imp validateImp(Imp imp) {
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException("SmartRTB only supports banner and video");
        }
        return imp;
    }

    private ExtImpSmartrtb parseImpExt(Imp imp) {
        try {
            return Json.mapper.convertValue(imp.getExt(), SMARTRTB_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static MultiMap resolveHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers()
                .add("x-openrtb-version", "2.5");
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, "User-Agent", device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, "X-Forwarded-For", device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, "Accept-Language", device.getLanguage());

            final Integer dnt = device.getDnt();
            if (dnt != null) {
                headers.add("DNT", dnt.toString());
            }
        }
        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        if (httpCall.getResponse().getStatusCode() == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }

        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return extractBids(httpCall.getRequest().getPayload(), bidResponse);
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static Result<List<BidderBid>> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }
        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> bidFromResponse(bidRequest.getImp(), bid, errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return Result.of(bidderBids, errors);
    }

    private static BidderBid bidFromResponse(List<Imp> imps, Bid bid, List<BidderError> errors) {
        try {
            final BidType bidType = getBidType(bid.getImpid(), imps);
            return BidderBid.of(bid, bidType, DEFAULT_BID_CURRENCY);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                }
            }
        }
        throw new PreBidException(String.format("Failed to find impression %s", impId));
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
