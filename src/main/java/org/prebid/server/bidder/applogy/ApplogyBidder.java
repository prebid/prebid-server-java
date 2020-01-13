package org.prebid.server.bidder.applogy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
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
import org.prebid.server.proto.openrtb.ext.request.applogy.ExtImpApplogy;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ApplogyBidder implements Bidder<BidRequest> {

    private final String endpointUrl;

    private static final String DEFAULT_BID_CURRENCY = "USD";

    public ApplogyBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();
        for (Imp imp : request.getImp()) {
            if (imp.getBanner() == null && imp.getVideo() == null && imp.getXNative() == null) {
                errors.add(BidderError.badInput(
                        String.format("Applogy only supports banner, video and native media types. Ignoring imp id=%s",
                                imp.getId())));
                continue;
            }
            validImps.add(processImp(imp));
        }

        if (validImps.isEmpty()) {
            errors.add(BidderError.badInput("No valid impressions in the bid request"));
            return Result.of(Collections.emptyList(), errors);
        }

        final ExtImpApplogy firstImpExt;
        try {
            firstImpExt = parseAndValidateImpExt(validImps.get(0));
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badInput(e.getMessage()));
        }

        final BidRequest outgoingRequest = request.toBuilder().imp(validImps).build();
        final String body = Json.encode(outgoingRequest);
        final String requestUrl = endpointUrl + "/applogy-exchange/" + firstImpExt.getToken();
        final MultiMap headers = resolveHeaders();

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

    private MultiMap resolveHeaders() {
        return HttpUtil.headers();
    }

    private ExtImpApplogy parseAndValidateImpExt(Imp imp) {
        final ExtImpApplogy extImpApplogy;
        try {
            extImpApplogy = Json.mapper.convertValue(imp.getExt().get("bidder"), ExtImpApplogy.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        if (StringUtils.isBlank(extImpApplogy.getToken())) {
            throw new PreBidException("token is empty");
        }
        return extImpApplogy;
    }

    private Imp processImp(Imp imp) {
        Banner banner = imp.getBanner();
        if (banner != null) {
            if (banner.getH() == null && banner.getW() == null && CollectionUtils.isNotEmpty(banner.getFormat())) {
                final Format firstFormat = banner.getFormat().get(0);
                final Banner modifiedBanner = banner.toBuilder()
                        .h(firstFormat.getH())
                        .w(firstFormat.getW())
                        .build();
                return imp.toBuilder().banner(modifiedBanner).build();
            }
        }
        return imp;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        if (httpCall.getResponse().getStatusCode() == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }
        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());

        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse("failed to decode json"));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        final Map<String, BidType> requestImpIdToBidType = bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, ApplogyBidder::getBidType));

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid,
                        requestImpIdToBidType.getOrDefault(bid.getImpid(), BidType.banner), DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(Imp imp) {
        if (imp.getBanner() != null) {
            return BidType.banner;
        } else if (imp.getVideo() != null) {
            return BidType.video;
        } else if (imp.getXNative() != null) {
            return BidType.xNative;
        } else {
            throw new PreBidException(
                    String.format("ignoring bid, request doesn't contain any valid impression with id=%s", imp.getId())
            );
        }
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
