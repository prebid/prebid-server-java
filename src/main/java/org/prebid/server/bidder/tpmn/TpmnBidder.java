package org.prebid.server.bidder.tpmn;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.frvradn.model.ExtImpFrvrAdn;
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
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TpmnBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpFrvrAdn>> FRVRADN_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    public TpmnBidder(String endpointUrl,
                      CurrencyConversionService currencyConversionService,
                      JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(endpointUrl);
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final Imp modifiedImp = modifyImp(imp, bidRequest);
                requests.add(makeHttpRequest(bidRequest, modifiedImp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private ExtImpFrvrAdn parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), FRVRADN_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp modifyImp(Imp imp, BidRequest bidRequest) {
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

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest bidRequest, Imp imp) {
        final BidRequest modifiedBidRequest = bidRequest.toBuilder()
                .imp(Collections.singletonList(imp))
                .build();
        return BidderUtil.defaultRequest(modifiedBidRequest, endpointUrl, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bids = extractBids(httpCall.getRequest().getPayload(), bidResponse, errors);

        return Result.of(bids, errors);
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest,
                                               BidResponse bidResponse,
                                               List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, bidRequest.getImp(), bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, List<Imp> imps, String currency, List<BidderError> errors) {
        try {
            return BidderBid.of(bid, resolveBidType(bid), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType resolveBidType(Bid bid) throws PreBidException {
        final Integer markupType = bid.getMtype();
        if (markupType == null) {
            throw new PreBidException("Missing MType for bid: " + bid.getId());
        }
        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            default ->
                    throw new PreBidException("Unsupported MType: %s, for bid: %s".formatted(markupType, bid.getId()));
        };
    }
}

