package org.prebid.server.bidder.stroeercore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.stroeercore.model.StroeerCoreBid;
import org.prebid.server.bidder.stroeercore.model.StroeerCoreBidResponse;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.stroeercore.ExtImpStroeerCore;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class StroeerCoreBidder implements Bidder<BidRequest> {

    private static final String BIDDER_CURRENCY = "EUR";
    private static final TypeReference<ExtPrebid<?, ExtImpStroeerCore>> STROEER_CORE_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final CurrencyConversionService currencyConversionService;

    public StroeerCoreBidder(String endpointUrl,
                             JacksonMapper mapper,
                             CurrencyConversionService currencyConversionService) {
        this.endpointUrl = HttpUtil.validateUrl(endpointUrl);
        this.mapper = Objects.requireNonNull(mapper);
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<Imp> modifiedImps = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            final ExtImpStroeerCore impExt;
            final Price price;

            try {
                validateImp(imp);

                impExt = parseImpExt(imp);
                validateImpExt(impExt);

                price = convertBidFloor(bidRequest, imp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput("%s. Ignore imp id = %s.".formatted(e.getMessage(), imp.getId())));
                continue;
            }

            modifiedImps.add(modifyImp(imp, impExt, price));
        }

        if (modifiedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest outgoingRequest = bidRequest.toBuilder().imp(modifiedImps).build();

        return createHttpRequests(errors, outgoingRequest);
    }

    private static void validateImp(Imp imp) {
        if (imp.getBanner() == null) {
            throw new PreBidException("Expected banner impression");
        }
    }

    private ExtImpStroeerCore parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), STROEER_CORE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static void validateImpExt(ExtImpStroeerCore impExt) {
        if (StringUtils.isBlank(impExt.getSlotId())) {
            throw new PreBidException("Custom param slot id (sid) is empty");
        }
    }

    private Price convertBidFloor(BidRequest bidRequest, Imp imp) {
        final BigDecimal bidFloor = imp.getBidfloor();
        final String bidFloorCurrency = imp.getBidfloorcur();

        if (!shouldConvertBidFloor(bidFloor, bidFloorCurrency)) {
            return Price.of(bidFloorCurrency, bidFloor);
        }

        final BigDecimal convertedBidFloor = currencyConversionService.convertCurrency(
                bidFloor, bidRequest, bidFloorCurrency, BIDDER_CURRENCY);

        return Price.of(BIDDER_CURRENCY, convertedBidFloor);
    }

    private Result<List<HttpRequest<BidRequest>>> createHttpRequests(List<BidderError> errors, BidRequest bidRequest) {
        return Result.of(Collections.singletonList(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .body(mapper.encodeToBytes(bidRequest))
                .payload(bidRequest)
                .headers(HttpUtil.headers())
                .build()), errors);
    }

    private static boolean shouldConvertBidFloor(BigDecimal bidFloor, String bidFloorCurrency) {
        return BidderUtil.isValidPrice(bidFloor) && !StringUtils.equalsIgnoreCase(bidFloorCurrency, BIDDER_CURRENCY);
    }

    private static Imp modifyImp(Imp imp, ExtImpStroeerCore impExt, Price price) {
        return imp.toBuilder()
                .bidfloorcur(price.getCurrency())
                .bidfloor(price.getValue())
                .tagid(impExt.getSlotId())
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final String body = httpCall.getResponse().getBody();
            final StroeerCoreBidResponse response = mapper.decodeValue(body, StroeerCoreBidResponse.class);
            return Result.withValues(extractBids(response));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(StroeerCoreBidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getBids())) {
            return Collections.emptyList();
        }

        return bidResponse.getBids().stream()
                .filter(Objects::nonNull)
                .map(StroeerCoreBidder::toBidderBid)
                .toList();
    }

    private static BidderBid toBidderBid(StroeerCoreBid stroeercoreBid) {
        return BidderBid.of(
                Bid.builder()
                        .id(stroeercoreBid.getId())
                        .impid(stroeercoreBid.getBidId())
                        .w(stroeercoreBid.getWidth())
                        .h(stroeercoreBid.getHeight())
                        .price(stroeercoreBid.getCpm())
                        .adm(stroeercoreBid.getAdMarkup())
                        .crid(stroeercoreBid.getCreativeId())
                        .build(),
                BidType.banner,
                BIDDER_CURRENCY);
    }
}
