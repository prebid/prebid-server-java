package org.prebid.server.bidder.adverxo;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adverxo.ExtImpAdverxo;
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

public class AdverxoBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdverxo>> ADVERXO_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final String ADUNIT_MACROS_ENDPOINT = "{{adUnitId}}";
    private static final String AUTH_MACROS_ENDPOINT = "{{auth}}";
    private static final String PRICE_MACRO = "${AUCTION_PRICE}";

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final CurrencyConversionService currencyConversionService;

    public AdverxoBidder(String endpointUrl, JacksonMapper mapper,
                         CurrencyConversionService currencyConversionService) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = mapper;
        this.currencyConversionService = currencyConversionService;
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpAdverxo extImp = parseImpExt(imp);
                final String endpoint = resolveEndpoint(extImp);
                final Imp modifiedImp = modifyImp(request, imp);
                final BidRequest outgoingRequest = createRequest(request, modifiedImp);

                requests.add(BidderUtil.defaultRequest(outgoingRequest, endpoint, mapper));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private ExtImpAdverxo parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADVERXO_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error parsing ext.imp.bidder: " + e.getMessage());
        }
    }

    private String resolveEndpoint(ExtImpAdverxo extImp) {
        return endpointUrl
                .replace(ADUNIT_MACROS_ENDPOINT, Objects.toString(extImp.getAdUnitId(), "0"))
                .replace(AUTH_MACROS_ENDPOINT, HttpUtil.encodeUrl(StringUtils.defaultString(extImp.getAuth())));
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

    private static BidRequest createRequest(BidRequest originalRequest, Imp modifiedImp) {
        return originalRequest.toBuilder()
                .imp(Collections.singletonList(modifiedImp))
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
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
                .map(bid -> makeBid(bid, bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private BidderBid makeBid(Bid bid, String currency) {
        final BidType bidType = getBidType(bid.getMtype());
        final String resolvedAdm = bidType == BidType.xNative ? resolveAdm(bid.getAdm(), bid.getPrice()) : bid.getAdm();
        final Bid processedBid = processBidMacros(bid, resolvedAdm);

        return BidderBid.of(processedBid, bidType, currency);
    }

    private static BidType getBidType(Integer mType) {
        return switch (mType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            case null, default -> throw new PreBidException("Unsupported mType " + mType);
        };
    }

    private static Bid processBidMacros(Bid bid, String adm) {
        final String price = bid.getPrice() != null ? bid.getPrice().toPlainString() : "0";

        return bid.toBuilder()
                .adm(replaceMacro(adm, price))
                .build();
    }

    private static String replaceMacro(String input, String value) {
        return input != null ? input.replace(PRICE_MACRO, value) : null;
    }

    private static String resolveAdm(String bidAdm, BigDecimal price) {
        return StringUtils.isNotBlank(bidAdm) ? bidAdm.replace("${AUCTION_PRICE}", String.valueOf(price)) : bidAdm;
    }
}
