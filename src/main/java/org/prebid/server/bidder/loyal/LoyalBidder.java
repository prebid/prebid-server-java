package org.prebid.server.bidder.loyal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.loyal.ExtImpLoyal;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class LoyalBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpLoyal>> LOYAL_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

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
                final HttpRequest<BidRequest> httpRequest = createHttpRequest(ext, request, imp);
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
        try {
            return mapper.mapper().convertValue(imp.getExt(), LOYAL_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Missing bidder ext in impression with id: " + imp.getId());
        }
    }

    private HttpRequest<BidRequest> createHttpRequest(ExtImpLoyal ext, BidRequest request, Imp imp) {
        // Utworzenie nowego ExtImpLoyal z odpowiednim typem
        final ExtImpLoyal modifiedExt;
        if (ext.getPlacementId() != null) {
            modifiedExt = ExtImpLoyal.of(ext.getPlacementId(), null, "publisher");
        } else if (ext.getEndpointId() != null) {
            modifiedExt = ExtImpLoyal.of(null, ext.getEndpointId(), "network");
        } else {
            throw new PreBidException("Both placementId and endpointId are missing in ExtImpLoyal");
        }

        // Utworzenie nowego ObjectNode z zakodowanym ExtImpLoyal
        final ObjectNode modifiedImpExt = mapper.mapper().valueToTree(ExtPrebid.of(null, modifiedExt));

        // Zaktualizowanie Imp z nowym ext
        final Imp modifiedImp = imp.toBuilder().ext(modifiedImpExt).build();

        // Zaktualizowanie BidRequest z nowym Imp
        final BidRequest modifiedRequest = request.toBuilder().imp(Collections.singletonList(modifiedImp)).build();

        // Utworzenie HttpRequest
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .body(mapper.encodeToBytes(modifiedRequest))
                .headers(HttpUtil.headers())
                .impIds(BidderUtil.impIds(modifiedRequest))
                .payload(modifiedRequest)
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

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid array");
        }
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid), bidResponse.getCur()))
                .toList();
    }

    private BidType getBidType(Bid bid) {
        final JsonNode typeNode = Optional.ofNullable(bid.getExt())
                .map(extNode -> extNode.get("prebid"))
                .map(extPrebidNode -> extPrebidNode.get("type"))
                .orElse(null);

        final BidType bidType;
        try {
            bidType = mapper.mapper().convertValue(typeNode, BidType.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to parse bid.ext.prebid.type for bid.id: '%s'"
                    .formatted(bid.getId()));
        }

        if (bidType == null) {
            throw new PreBidException("bid.ext.prebid.type is not present for bid.id: '%s'"
                    .formatted(bid.getId()));
        }

        return bidType;
    }

}
