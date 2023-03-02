package org.prebid.server.bidder.rtbhouse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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
import org.prebid.server.bidder.model.Price;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.rtbhouse.ExtImpRtbhouse;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RtbhouseBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpRtbhouse>> RTBHOUSE_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String BIDDER_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final CurrencyConversionService currencyConversionService;

    public RtbhouseBidder(String endpointUrl,
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
                final ExtImpRtbhouse impExt = parseImpExt(imp);
                final Price bidFloorPrice = resolveBidFloor(imp, impExt, bidRequest);

                modifiedImps.add(modifyImp(imp, bidFloorPrice));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (errors.size() > 0) {
            return Result.withErrors(errors);
        }

        final BidRequest outgoingRequest = bidRequest.toBuilder()
                .cur(Collections.singletonList(BIDDER_CURRENCY))
                .imp(modifiedImps)
                .build();

        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .headers(HttpUtil.headers())
                .uri(endpointUrl)
                .body(mapper.encodeToBytes(outgoingRequest))
                .payload(outgoingRequest)
                .build());
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

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, BidType.banner, bidResponse.getCur()))
                .toList();
    }

    private ExtImpRtbhouse parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), RTBHOUSE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static Imp modifyImp(Imp imp, Price bidFloorPrice) {

        return imp.toBuilder()
                .bidfloorcur(ObjectUtil.getIfNotNull(bidFloorPrice, Price::getCurrency))
                .bidfloor(ObjectUtil.getIfNotNull(bidFloorPrice, Price::getValue))
                .build();
    }

    private Price resolveBidFloor(Imp imp, ExtImpRtbhouse impExt, BidRequest bidRequest) {
        List<String> brCur = bidRequest.getCur();
        Price initialBidFloorPrice = Price.of(imp.getBidfloorcur(), imp.getBidfloor());

        BigDecimal impExtBidFloor = impExt.getBidFloor();
        String impExtCurrency = impExtBidFloor != null && brCur != null && brCur.size() > 0
                ? brCur.get(0) : null;
        Price impExtBidFloorPrice = Price.of(impExtCurrency, impExtBidFloor);
        Price resolvedPrice = initialBidFloorPrice.getValue() == null ? impExtBidFloorPrice : initialBidFloorPrice;

        return BidderUtil.isValidPrice(resolvedPrice)
                && !StringUtils.equalsIgnoreCase(resolvedPrice.getCurrency(), BIDDER_CURRENCY)
                ? convertBidFloor(resolvedPrice, imp.getId(), bidRequest)
                : resolvedPrice;
    }

    private Price convertBidFloor(Price bidFloorPrice, String impId, BidRequest bidRequest) {
        final String bidFloorCur = bidFloorPrice.getCurrency();
        try {
            final BigDecimal convertedPrice = currencyConversionService
                    .convertCurrency(bidFloorPrice.getValue(), bidRequest, bidFloorCur, BIDDER_CURRENCY);

            return Price.of(BIDDER_CURRENCY, convertedPrice);
        } catch (PreBidException e) {
            throw new PreBidException(String.format(
                    "Unable to convert provided bid floor currency from %s to %s for imp `%s`",
                    bidFloorCur, BIDDER_CURRENCY, impId));
        }
    }

}
