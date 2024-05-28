package org.prebid.server.bidder.yieldmo;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.bidder.yieldmo.proto.YieldmoBidExt;
import org.prebid.server.bidder.yieldmo.proto.YieldmoImpExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.yieldmo.ExtImpYieldmo;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class YieldmoBidder implements Bidder<BidRequest> {

    private static final JsonPointer PBADSLOT_POINTER = JsonPointer.valueOf("/data/pbadslot");
    private static final TypeReference<ExtPrebid<?, ExtImpYieldmo>> YIELDMO_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String USD_CURRENCY = "USD";

    private final String endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    public YieldmoBidder(String endpointUrl,
            CurrencyConversionService currencyConversionService,
            JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<Imp> modifiedImps = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpYieldmo impExt = parseImpExt(imp);
                modifiedImps.add(modifyImp(imp, bidRequest, impExt));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (modifiedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest modifiedRequest = bidRequest.toBuilder().imp(modifiedImps).build();
        return Result.of(Collections.singletonList(makeRequest(modifiedRequest)), errors);
    }

    private ExtImpYieldmo parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), YIELDMO_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private Imp modifyImp(Imp imp, BidRequest bidRequest, ExtImpYieldmo ext) {
        final YieldmoImpExt modifiedExt = YieldmoImpExt.of(ext.getPlacementId(), extractGpid(imp));

        Price bidFloorPrice = Price.of(imp.getBidfloorcur(), imp.getBidfloor());
        bidFloorPrice = BidderUtil.isValidPrice(bidFloorPrice)
                ? convertBidFloor(bidFloorPrice, imp.getId(), bidRequest) : bidFloorPrice;

        return imp.toBuilder()
            .bidfloor(bidFloorPrice.getValue())
            .bidfloorcur(bidFloorPrice.getCurrency())
            .ext(mapper.mapper().valueToTree(modifiedExt))
            .build();
    }

    private Price convertBidFloor(Price bidFloorPrice, String impId, BidRequest bidRequest) {
        final String bidFloorCur = bidFloorPrice.getCurrency();
        try {
            final BigDecimal convertedPrice = currencyConversionService
                    .convertCurrency(bidFloorPrice.getValue(), bidRequest, bidFloorCur, USD_CURRENCY);

            return Price.of(USD_CURRENCY, convertedPrice);
        } catch (PreBidException e) {
            //If currency conversion fails, we still want to recieve the bid request.
            return bidFloorPrice;
        }
    }

    private static String extractGpid(Imp imp) {
        final JsonNode pbadslotNode = imp.getExt().at(PBADSLOT_POINTER);
        return pbadslotNode.isTextual()
                ? StringUtils.defaultIfEmpty(pbadslotNode.asText(), null)
                : null;
    }

    private HttpRequest<BidRequest> makeRequest(BidRequest bidRequest) {
        return BidderUtil.defaultRequest(bidRequest, endpointUrl, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> {
                    final BidType bidType = resolveBidType(bid);
                    return bidType != null ? BidderBid.of(bid, bidType, bidResponse.getCur()) : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private BidType resolveBidType(Bid bid) {
        try {
            final YieldmoBidExt bidExt = mapper.mapper().treeToValue(bid.getExt(), YieldmoBidExt.class);
            return BidType.fromString(bidExt.getMediaType());
        } catch (Exception e) {
            return null;
        }
    }
}
