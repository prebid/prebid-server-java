package org.prebid.server.bidder.blis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
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
import org.prebid.server.proto.openrtb.ext.request.blis.ExtImpBlis;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class BlisBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpBlis>> BLIS_EXT_TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final String AUCTION_PRICE_MACRO = "${AUCTION_PRICE}";
    private static final String SUPPLY_ID_MACRO = "{{SupplyId}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public BlisBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final String supplyId;
        try {
            supplyId = parseImpExt(request.getImp().getFirst()).getSupplyId();
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        return Result.withValue(BidderUtil.defaultRequest(request, makeHeaders(supplyId), makeUrl(supplyId), mapper));
    }

    private ExtImpBlis parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), BLIS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error parsing imp.ext: " + e.getMessage());
        }
    }

    private static MultiMap makeHeaders(String supplyId) {
        return HttpUtil.headers().add("X-Supply-Partner-Id", supplyId);
    }

    private String makeUrl(String supplyId) {
        return endpointUrl.replace(SUPPLY_ID_MACRO, HttpUtil.encodeUrl(supplyId));
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, errors);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static BidderBid makeBid(Bid bid, String currency, List<BidderError> errors) {
        final BidType bidType = getBidType(bid, errors);
        return bidType != null
                ? BidderBid.of(resolveMacros(bid), bidType, currency)
                : null;
    }

    private static Bid resolveMacros(Bid bid) {
        final BigDecimal price = bid.getPrice();
        final String priceAsString = price != null ? price.toPlainString() : "0";

        return bid.toBuilder()
                .nurl(StringUtils.replace(bid.getNurl(), AUCTION_PRICE_MACRO, priceAsString))
                .adm(StringUtils.replace(bid.getAdm(), AUCTION_PRICE_MACRO, priceAsString))
                .burl(StringUtils.replace(bid.getBurl(), AUCTION_PRICE_MACRO, priceAsString))
                .build();
    }

    private static BidType getBidType(Bid bid, List<BidderError> errors) {
        return switch (bid.getMtype()) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            case null, default -> {
                errors.add(BidderError.badServerResponse(
                        "Failed to parse media type of impression ID " + bid.getImpid()));
                yield null;
            }
        };
    }
}
