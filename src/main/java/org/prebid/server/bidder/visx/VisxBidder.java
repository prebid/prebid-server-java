package org.prebid.server.bidder.visx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.visx.model.VisxBid;
import org.prebid.server.bidder.visx.model.VisxResponse;
import org.prebid.server.bidder.visx.model.VisxSeatBid;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class VisxBidder implements Bidder<BidRequest> {

    private static final String DEFAULT_REQUEST_CURRENCY = "USD";
    private static final Set<String> SUPPORTED_BID_TYPES_TEXTUAL = Set.of("banner", "video");

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public VisxBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        return Result.withValue(makeRequest(request));
    }

    private HttpRequest<BidRequest> makeRequest(BidRequest bidRequest) {
        final BidRequest outgoingRequest = modifyRequest(bidRequest);
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .body(mapper.encode(outgoingRequest))
                .headers(HttpUtil.headers())
                .payload(outgoingRequest)
                .build();
    }

    private BidRequest modifyRequest(BidRequest bidRequest) {
        return CollectionUtils.isEmpty(bidRequest.getCur())
                ? bidRequest.toBuilder().cur(Collections.singletonList(DEFAULT_REQUEST_CURRENCY)).build()
                : bidRequest;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final VisxResponse visxResponse = mapper.decodeValue(httpCall.getResponse().getBody(), VisxResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), visxResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, VisxResponse visxResponse) {
        if (visxResponse == null || CollectionUtils.isEmpty(visxResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, visxResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, VisxResponse visxResponse) {
        return visxResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(VisxSeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(visxBid -> toBidderBid(bidRequest, visxBid))
                .collect(Collectors.toList());
    }

    private BidderBid toBidderBid(BidRequest bidRequest, VisxBid visxBid) {
        final Bid bid = toBid(visxBid, bidRequest.getId());
        final BidType bidType = getBidType(bid.getExt(), bid.getImpid(), bidRequest.getImp());
        return BidderBid.of(bid, bidType, null);
    }

    private static Bid toBid(VisxBid visxBid, String id) {
        return Bid.builder()
                .id(id)
                .impid(visxBid.getImpid())
                .price(visxBid.getPrice())
                .adm(visxBid.getAdm())
                .crid(visxBid.getCrid())
                .dealid(visxBid.getDealid())
                .h(visxBid.getH())
                .w(visxBid.getW())
                .adomain(visxBid.getAdomain())
                .ext(visxBid.getExt())
                .build();
    }

    private static BidType getBidType(ObjectNode bidExt, String impId, List<Imp> imps) {
        final BidType extBidType = getBidTypeFromExt(bidExt);
        return extBidType != null ? extBidType : getBidTypeFromImp(impId, imps);
    }

    private static BidType getBidTypeFromExt(ObjectNode bidExt) {
        final JsonNode mediaTypeNode = bidExt != null ? bidExt.at("/prebid/meta/mediaType") : null;
        final String bidTypeTextual = mediaTypeNode != null && mediaTypeNode.isTextual()
                ? mediaTypeNode.asText()
                : null;

        return bidTypeTextual != null && SUPPORTED_BID_TYPES_TEXTUAL.contains(bidTypeTextual)
                ? BidType.valueOf(bidTypeTextual)
                : null;
    }

    private static BidType getBidTypeFromImp(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                }
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                throw new PreBidException(String.format("Unknown impression type for ID: \"%s\"", impId));
            }
        }
        throw new PreBidException(String.format("Failed to find impression for ID: \"%s\"", impId));
    }
}
