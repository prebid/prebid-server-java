package org.prebid.server.bidder.aso;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.aso.ExtImpAso;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class AsoBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAso>> EXT_TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String ZONE_MACRO = "{{ZoneID}}";
    private static final String PRICE_MACRO = "${AUCTION_PRICE}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AsoBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpAso extImp = parseImpExt(imp);
                final BidRequest modifiedRequest = modifyBidRequest(request, imp);
                httpRequests.add(makeHttpRequest(modifiedRequest, extImp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpAso parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Missing bidder ext in impression with id: " + imp.getId());
        }
    }

    private BidRequest modifyBidRequest(BidRequest request, Imp imp) {
        return request.toBuilder()
                .imp(Collections.singletonList(imp))
                .build();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, ExtImpAso extImp) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(makeUrl(extImp.getZone()))
                .impIds(BidderUtil.impIds(request))
                .headers(HttpUtil.headers())
                .payload(request)
                .body(mapper.encodeToBytes(request))
                .build();
    }

    private String makeUrl(Integer zone) {
        return endpointUrl.replace(ZONE_MACRO, zone.toString());
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, errors);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> makeBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid makeBid(Bid bid, String currency, List<BidderError> errors) {
        final BidType mediaType = getMediaType(bid, errors);

        if (mediaType == null) {
            return null;
        }

        final BigDecimal price = bid.getPrice();
        final String priceAsString = price != null ? price.toPlainString() : "0";

        final Bid modifiedBid = bid.toBuilder()
                .nurl(StringUtils.replace(bid.getNurl(), PRICE_MACRO, priceAsString))
                .adm(StringUtils.replace(bid.getAdm(), PRICE_MACRO, priceAsString))
                .build();

        return BidderBid.of(modifiedBid, mediaType, currency);
    }

    private BidType getMediaType(Bid bid, List<BidderError> errors) {
        try {
            return Optional.ofNullable(bid.getExt())
                    .map(ext -> mapper.mapper().convertValue(ext, EXT_PREBID_TYPE_REFERENCE))
                    .map(ExtPrebid::getPrebid)
                    .map(ExtBidPrebid::getType)
                    .orElseThrow(IllegalArgumentException::new);
        } catch (IllegalArgumentException e) {
            errors.add(BidderError.badServerResponse("Failed to get type of bid \"%s\"".formatted(bid.getImpid())));
            return null;
        }
    }
}
