package org.prebid.server.bidder.visiblemeasures;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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
import org.prebid.server.bidder.visiblemeasures.model.VisibleMeasuresType;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.visiblemeasures.ExtImpVisibleMeasures;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class VisibleMeasuresBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpVisibleMeasures>> VISIBLE_MEASURES_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public VisibleMeasuresBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            final ExtImpVisibleMeasures extImpVisibleMeasures;
            try {
                extImpVisibleMeasures = parseImpExt(imp);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }

            final Imp updatedImp = updateImp(imp, extImpVisibleMeasures);
            final BidRequest updatedBidRequest = makeBidRequest(bidRequest, updatedImp);
            requests.add(makeHttpRequest(updatedBidRequest));
        }

        return Result.withValues(requests);
    }

    private ExtImpVisibleMeasures parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), VISIBLE_MEASURES_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp updateImp(Imp imp, ExtImpVisibleMeasures extImpVisibleMeasures) {
        return imp.toBuilder()
                .ext(resolveImpExt(extImpVisibleMeasures))
                .build();
    }

    private ObjectNode resolveImpExt(ExtImpVisibleMeasures extImpVisibleMeasures) {
        final String placementId = extImpVisibleMeasures.getPlacementId();
        final String endpointId = extImpVisibleMeasures.getEndpointId();

        final VisibleMeasuresType type = ObjectUtils.defaultIfNull(
                StringUtils.isNotEmpty(placementId) ? VisibleMeasuresType.PUBLISHER : null,
                StringUtils.isNotEmpty(endpointId) ? VisibleMeasuresType.NETWORK : null);

        final ExtImpVisibleMeasures resolvedImpExtBidder = ExtImpVisibleMeasures.of(
                type != null ? type.getValue() : StringUtils.EMPTY,
                type == VisibleMeasuresType.PUBLISHER ? placementId : null,
                type == VisibleMeasuresType.NETWORK ? endpointId : null);

        return mapper.mapper().valueToTree(ExtPrebid.of(null, resolvedImpExtBidder));
    }

    private static BidRequest makeBidRequest(BidRequest bidRequest, Imp imp) {
        return bidRequest.toBuilder().imp(Collections.singletonList(imp)).build();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest outgoingRequest) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .body(mapper.encodeToBytes(outgoingRequest))
                .payload(outgoingRequest)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, httpCall.getRequest().getPayload()));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid, bidRequest.getImp()), bidResponse.getCur()))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidType getBidType(Bid bid, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(bid.getImpid())) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
            }
        }

        throw new PreBidException("Failed to find impression \"%s\"".formatted(bid.getImpid()));
    }
}
