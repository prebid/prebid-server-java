package org.prebid.server.bidder.edge226;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.edge226.Edge226ImpExt;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Edge226Bidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, Edge226ImpExt>> EXT_TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final String PUBLISHER_IMP_EXT_TYPE = "publisher";
    private static final String NETWORK_IMP_EXT_TYPE = "network";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public Edge226Bidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final Edge226ImpExt impExt = parseImpExt(imp);
                final BidRequest modifiedBidRequest = makeRequest(request, imp, impExt);
                httpRequests.add(makeHttpRequest(modifiedBidRequest, imp.getId()));
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.withValues(httpRequests);
    }

    private Edge226ImpExt parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Invalid ext. Imp.Id: " + imp.getId());
        }
    }

    private BidRequest makeRequest(BidRequest request, Imp imp, Edge226ImpExt impExt) {
        final boolean hasPlacement = StringUtils.isNotBlank(impExt.getPlacementId());
        final boolean hasEndpoint = !hasPlacement && StringUtils.isNotBlank(impExt.getEndpointId());
        final Edge226ImpExt modifiedImpExt = impExt.toBuilder()
                .placementId(hasPlacement ? impExt.getPlacementId() : null)
                .endpointId(hasEndpoint ? impExt.getEndpointId() : null)
                .type(hasPlacement ? PUBLISHER_IMP_EXT_TYPE : hasEndpoint ? NETWORK_IMP_EXT_TYPE : null)
                .build();

        final ObjectNode ext = mapper.mapper().valueToTree(ExtPrebid.of(null, modifiedImpExt));
        return request.toBuilder().imp(Collections.singletonList(imp.toBuilder().ext(ext).build())).build();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest bidRequest, String impId) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .headers(HttpUtil.headers())
                .impIds(Collections.singleton(impId))
                .uri(endpointUrl)
                .body(mapper.encodeToBytes(bidRequest))
                .payload(bidRequest)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse("Bad Server Response"));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid array");
        }

        return bidResponse.getSeatbid().stream()
                .flatMap(seatBid -> Optional.ofNullable(seatBid.getBid()).orElse(List.of()).stream())
                .map(bid -> BidderBid.of(bid, getBidMediaType(bid), bidResponse.getCur()))
                .toList();
    }

    private static BidType getBidMediaType(Bid bid) {
        final Integer markupType = bid.getMtype();
        if (markupType == null) {
            throw new PreBidException("Missing MType for bid: " + bid.getId());
        }

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException(
                    "Unable to fetch mediaType " + bid.getMtype() + " in multi-format: " + bid.getImpid());
        };
    }
}
