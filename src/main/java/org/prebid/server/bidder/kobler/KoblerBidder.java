package org.prebid.server.bidder.kobler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.kobler.ExtImpKobler;
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

public class KoblerBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpKobler>> KOBLER_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String EXT_PREBID = "prebid";
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final String DEV_ENDPOINT = "https://bid-service.dev.essrtb.com/bid/prebid_server_rtb_call";

    private final String endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    public KoblerBidder(String endpointUrl,
                        CurrencyConversionService currencyConversionService,
                        JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(endpointUrl);
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        boolean testMode = false;
        final List<Imp> modifiedImps = new ArrayList<>();

        final List<String> currencies = new ArrayList<>(bidRequest.getCur());
        if (!currencies.contains(DEFAULT_BID_CURRENCY)) {
            currencies.add(DEFAULT_BID_CURRENCY);
        }

        final List<Imp> imps = bidRequest.getImp();
        if (!imps.isEmpty()) {
            try {
                testMode = parseImpExt(imps.getFirst()).getTest();
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                return Result.withErrors(errors);
            }
        }

        for (Imp imp : imps) {
            try {
                modifiedImps.add(modifyImp(bidRequest, imp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                return Result.withErrors(errors);
            }
        }

        final BidRequest modifiedRequest = bidRequest.toBuilder()
                .cur(currencies)
                .imp(modifiedImps)
                .build();

        final String endpoint = testMode ? DEV_ENDPOINT : endpointUrl;

        final HttpRequest<BidRequest> httpRequest = BidderUtil.defaultRequest(modifiedRequest, endpoint, mapper);
        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private Imp modifyImp(BidRequest bidRequest, Imp imp) {
        final Price resolvedBidFloor = resolveBidFloor(imp, bidRequest);

        return imp.toBuilder()
                .bidfloor(resolvedBidFloor.getValue())
                .bidfloorcur(resolvedBidFloor.getCurrency())
                .build();
    }

    private Price resolveBidFloor(Imp imp, BidRequest bidRequest) {
        final Price initialBidFloorPrice = Price.of(imp.getBidfloorcur(), imp.getBidfloor());
        return BidderUtil.shouldConvertBidFloor(initialBidFloorPrice, DEFAULT_BID_CURRENCY)
                ? convertBidFloor(initialBidFloorPrice, bidRequest)
                : initialBidFloorPrice;
    }

    private Price convertBidFloor(Price bidFloorPrice, BidRequest bidRequest) {
        final BigDecimal convertedPrice = currencyConversionService.convertCurrency(
                bidFloorPrice.getValue(),
                bidRequest,
                bidFloorPrice.getCurrency(),
                DEFAULT_BID_CURRENCY);

        return Price.of(DEFAULT_BID_CURRENCY, convertedPrice);
    }

    private ExtImpKobler parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), KOBLER_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
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
        return bidsFromResponse(bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid), bidResponse.getCur()))
                .toList();
    }

    private BidType getBidType(Bid bid) {
        return Optional.ofNullable(bid.getExt())
                .map(ext -> ext.get(EXT_PREBID))
                .map(ObjectNode.class::cast)
                .map(this::parseExtBidPrebid)
                .map(ExtBidPrebid::getType)
                .orElse(BidType.banner);
    }

    private ExtBidPrebid parseExtBidPrebid(ObjectNode prebid) {
        try {
            return mapper.mapper().treeToValue(prebid, ExtBidPrebid.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
