package org.prebid.server.bidder.cpmstar;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.cpmstar.ExtImpCPMStar;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class CPMStarBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpCPMStar>> CPM_STAR_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpCPMStar>>() {
            };

    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;

    public CPMStarBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        try {
            final BidRequest bidRequest = processRequest(request, errors);

            return Result.of(Collections.singletonList(createSingleRequest(bidRequest)), errors);
        } catch (IllegalArgumentException e) {
            return Result.of(Collections.emptyList(),
                    Collections.singletonList(BidderError.badInput(e.getMessage()))
            );
        }
    }

    private BidRequest processRequest(BidRequest bidRequest, List<BidderError> errors) {
        if (CollectionUtils.isEmpty(bidRequest.getImp())) {
            throw new IllegalArgumentException("No Imps in Bid Request");
        }

        final List<Imp> validImpList = new ArrayList<>();
        for (final Imp imp : bidRequest.getImp()) {
            try {
                validImpList.add(parseAndValidateImp(imp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        return bidRequest.toBuilder().imp(validImpList).build();
    }

    private Imp parseAndValidateImp(Imp imp) {
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException("Only Banner and Video bid-types are supported at this time");
        }

        final ExtPrebid<?, ExtImpCPMStar> ext =
                Json.mapper.convertValue(imp.getExt(), CPM_STAR_EXT_TYPE_REFERENCE);

        final ExtImpCPMStar bidder = ext.getBidder();
        if (bidder == null) {
            throw new PreBidException(String.format("imp id=%s: bidder.ext is null", imp.getId()));
        }
        return imp.toBuilder().ext(Json.mapper.valueToTree(ext)).build();
    }

    private HttpRequest<BidRequest> createSingleRequest(BidRequest request) {

        final String body = Json.encode(request);

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .body(body)
                .payload(request)
                .build();
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

    private Result<List<BidderBid>> extractBids(BidRequest request, BidResponse bidResponse) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }
        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> bidFromResponse(request.getImp(), bid, errors))
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
