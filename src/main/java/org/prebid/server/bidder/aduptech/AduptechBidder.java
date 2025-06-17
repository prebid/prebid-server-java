package org.prebid.server.bidder.aduptech;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
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
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AduptechBidder implements Bidder<BidRequest> {

    private static final String COMPONENT_ID_HEADER = "Componentid";
    private static final String COMPONENT_ID_HEADER_VALUE = "prebid-java";
    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final CurrencyConversionService currencyConversionService;
    private final String targetCurrency;

    public AduptechBidder(String endpointUrl,
                          JacksonMapper mapper,
                          CurrencyConversionService currencyConversionService,
                          String targetCurrency) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.targetCurrency = validateCurrency(targetCurrency);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> modifiedImps = new ArrayList<>(request.getImp().size());
        for (Imp imp : request.getImp()) {
            try {
                modifiedImps.add(modifyImp(imp, request));
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
        }

        final BidRequest outgoingRequest = request.toBuilder().imp(modifiedImps).build();
        final HttpRequest<BidRequest> httpRequest = BidderUtil.defaultRequest(
                outgoingRequest,
                makeHeaders(),
                endpointUrl,
                mapper);

        return Result.withValue(httpRequest);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static String validateCurrency(String code) {
        try {
            Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid extra info: invalid TargetCurrency %s".formatted(code));
        }
        return code.toUpperCase();
    }

    private static MultiMap makeHeaders() {
        return HttpUtil.headers().add(COMPONENT_ID_HEADER, COMPONENT_ID_HEADER_VALUE);
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
        try {
            return BidderBid.of(bid, getBidType(bid.getMtype()), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType getBidType(Integer markupType) {
        return switch (markupType) {
            case 1 -> BidType.banner;
            case 4 -> BidType.xNative;
            case null, default -> throw new PreBidException("Unknown markup type: " + markupType);
        };
    }

    private Imp modifyImp(Imp imp, BidRequest bidRequest) {
        Price impFloorPrice = Price.of(imp.getBidfloorcur(), imp.getBidfloor());
        impFloorPrice = BidderUtil.isValidPrice(impFloorPrice)
                && !targetCurrency.equalsIgnoreCase(impFloorPrice.getCurrency())
                ? convertBidFloor(impFloorPrice, bidRequest)
                : impFloorPrice;

        return imp.toBuilder()
                .bidfloor(impFloorPrice.getValue())
                .bidfloorcur(impFloorPrice.getCurrency())
                .build();
    }

    private Price convertBidFloor(Price impFloorPrice, BidRequest bidRequest) {
        try {
            return convertToTargetCurrency(impFloorPrice.getValue(), bidRequest, impFloorPrice.getCurrency());
        } catch (PreBidException e) {
            final BigDecimal defaultCurrencyBidFloor = currencyConversionService.convertCurrency(
                    impFloorPrice.getValue(),
                    bidRequest,
                    impFloorPrice.getCurrency(),
                    DEFAULT_BID_CURRENCY);
            return convertToTargetCurrency(defaultCurrencyBidFloor, bidRequest, DEFAULT_BID_CURRENCY);
        }
    }

    private Price convertToTargetCurrency(BigDecimal impFloorPrice, BidRequest bidRequest, String fromCurrency) {
        final BigDecimal convertedFloor = currencyConversionService.convertCurrency(
                impFloorPrice,
                bidRequest,
                fromCurrency,
                targetCurrency);

        return Price.of(targetCurrency, convertedFloor);
    }
}
