package org.prebid.server.bidder.seedtag;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.prebid.server.proto.openrtb.ext.request.seedtag.ExtImpSeedtag;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SeedtagBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSeedtag>> SEEDTAG_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String BIDDER_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final CurrencyConversionService currencyConversionService;

    public SeedtagBidder(String endpointUrl,
                         CurrencyConversionService currencyConversionService,
                         JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> modifiedImps = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final Price bidFloorPrice = resolveBidFloor(imp, request);

                modifiedImps.add(modifyImp(imp, bidFloorPrice));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (modifiedImps.size() < 1) {
            return Result.withErrors(errors);
        }

        final BidRequest modifiedBidRequest = request.toBuilder()
                .imp(modifiedImps)
                .build();
        return Result.of(
                Collections.singletonList(BidderUtil.defaultRequest(modifiedBidRequest, endpointUrl, mapper)),
                errors);
    }

    private static Imp modifyImp(Imp imp, Price bidFloorPrice) {
        return imp.toBuilder()
                .bidfloorcur(bidFloorPrice.getCurrency())
                .bidfloor(bidFloorPrice.getValue())
                .build();
    }

    private Price resolveBidFloor(Imp imp, BidRequest bidRequest) {
        final Price initialBidFloorPrice = Price.of(imp.getBidfloorcur(), imp.getBidfloor());
        return BidderUtil.shouldConvertBidFloor(initialBidFloorPrice, BIDDER_CURRENCY)
                ? convertBidFloor(initialBidFloorPrice, imp.getId(), bidRequest)
                : initialBidFloorPrice;
    }

    private Price convertBidFloor(Price bidFloorPrice, String impId, BidRequest bidRequest) {
        final BigDecimal convertedPrice = currencyConversionService.convertCurrency(
                bidFloorPrice.getValue(),
                bidRequest,
                bidFloorPrice.getCurrency(),
                BIDDER_CURRENCY);

        return Price.of(BIDDER_CURRENCY, convertedPrice);
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            final List<BidderBid> bidderBids = extractBids(bidResponse, errors);
            return Result.of(bidderBids, errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, List<BidderError> errors) {
        final BidType bidType;
        try {
            bidType = getBidType(bid);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }

        return BidderBid.of(bid, bidType, BIDDER_CURRENCY);
    }

    private static BidType getBidType(Bid bid) {
        return switch (bid.getMtype()) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            default -> throw new PreBidException("Invalid bid.mtype for bid.id: '%s'".formatted(bid.getId()));
        };
    }
}
