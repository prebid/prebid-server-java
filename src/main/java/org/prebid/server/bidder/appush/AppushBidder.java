package org.prebid.server.bidder.appush;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.appush.proto.AppushImpExtBidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.appush.ExtImpAppush;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AppushBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAppush>> APPUSH_EXT_TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final String PUBLISHER_PROPERTY = "publisher";
    private static final String NETWORK_PROPERTY = "network";
    private static final String BIDDER_PROPERTY = "bidder";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AppushBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {

        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            final ExtImpAppush extImpAppush;
            try {
                extImpAppush = parseExtImp(imp);
            } catch (IllegalArgumentException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }

            final Imp modifiedImp = modifyImp(imp, extImpAppush);
            httpRequests.add(makeHttpRequest(request, modifiedImp));
        }

        return Result.withValues(httpRequests);
    }

    private Imp modifyImp(Imp imp, ExtImpAppush extImpAppush) {

        final AppushImpExtBidder impExtAppushWithType = resolveImpExt(extImpAppush);

        final ObjectNode modifiedImpExtBidder = mapper.mapper().createObjectNode();

        modifiedImpExtBidder.set(BIDDER_PROPERTY, mapper.mapper().valueToTree(impExtAppushWithType));

        return imp.toBuilder().ext(modifiedImpExtBidder).build();
    }

    private AppushImpExtBidder resolveImpExt(ExtImpAppush extImpAppush) {

        final AppushImpExtBidder.AppushImpExtBidderBuilder builder = AppushImpExtBidder.builder();

        if (StringUtils.isNotEmpty(extImpAppush.getPlacementId())) {
            builder.type(PUBLISHER_PROPERTY).placementId(extImpAppush.getPlacementId());
        } else if (StringUtils.isNotEmpty(extImpAppush.getEndpointId())) {
            builder.type(NETWORK_PROPERTY).endpointId(extImpAppush.getEndpointId());
        }

        return builder.build();
    }

    private ExtImpAppush parseExtImp(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), APPUSH_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, Imp imp) {
        final BidRequest outgoingRequest = request.toBuilder().imp(List.of(imp)).build();

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .payload(outgoingRequest)
                .body(mapper.encodeToBytes(outgoingRequest))
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            List<BidderBid> bids = extractBids(httpCall.getRequest().getPayload(), bidResponse);
            Result.empty();
            return Result.withValues(bids);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .toList();
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
                }
                break;
            }
        }

        throw new PreBidException(String.format("Failed to find impression for ID: '%s'", impId));
    }
}
