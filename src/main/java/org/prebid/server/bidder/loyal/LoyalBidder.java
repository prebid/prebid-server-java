package org.prebid.server.bidder.loyal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.loyal.ExtImpLoyal;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class LoyalBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpLoyal>> LOYAL_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String PLACEMENT_ID_MACRO = "{{PlacementId}}";
    private static final String ENDPOINT_ID_MACRO = "{{EndpointId}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public LoyalBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpLoyal ext = parseImpExt(imp);
                final HttpRequest<BidRequest> httpRequest = createHttpRequest(ext, request);
                requests.add(httpRequest);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (!errors.isEmpty()) {
            return Result.withErrors(errors);
        }

        return Result.withValues(requests);
    }

    private ExtImpLoyal parseImpExt(Imp imp) {
        final ExtImpLoyal extImpLoyal;
        try {
            extImpLoyal = mapper.mapper().convertValue(imp.getExt(), LOYAL_EXT_TYPE_REFERENCE).getBidder();
            return extImpLoyal;
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Missing bidder ext in impression with id: " + imp.getId());
        }
    }

    private HttpRequest<BidRequest> createHttpRequest(ExtImpLoyal ext, BidRequest request) {
        String url = endpointUrl;
        url = StringUtils.isNotBlank(ext.getPlacementId())
                ? url.replace(PLACEMENT_ID_MACRO, ext.getPlacementId()) : url.replace("param={{PlacementId}}&", "");
        url = StringUtils.isNotBlank(ext.getEndpointId())
                ? url.replace(ENDPOINT_ID_MACRO, ext.getEndpointId()) : url.replace("&param2={{EndpointId}}", "");
        final BidRequest outgoingRequest = request.toBuilder().build();
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(url)
                .body(mapper.encodeToBytes(outgoingRequest))
                .headers(HttpUtil.headers())
                .impIds(BidderUtil.impIds(outgoingRequest))
                .payload(outgoingRequest)
                .build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid array");
        }
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
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
