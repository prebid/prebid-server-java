package org.prebid.server.bidder.displayio;

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
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.displayio.DisplayioImpExt;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class DisplayioBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, DisplayioImpExt>> TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final String BIDDER_CURRENCY = "USD";
    private static final String PUBLISHER_ID_MACRO = "{{PublisherID}}";
    private static final String X_OPENRTB_VERSION = "2.5";

    private final CurrencyConversionService currencyConversionService;
    private final String endpointUrl;
    private final JacksonMapper mapper;

    public DisplayioBidder(CurrencyConversionService currencyConversionService,
                           String endpointUrl,
                           JacksonMapper mapper) {

        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final DisplayioImpExt impExt = parseImpExt(imp);

                final BidRequest modifiedBidRequest = request.toBuilder()
                        .imp(Collections.singletonList(modifyImp(request, imp)))
                        .ext(modifyExtRequest(request, impExt))
                        .build();

                final String url = resolveEndpoint(impExt);
                requests.add(BidderUtil.defaultRequest(modifiedBidRequest, makeHeaders(), url, mapper));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return CollectionUtils.isEmpty(requests)
                ? Result.withErrors(errors)
                : Result.of(requests, errors);

    }

    private DisplayioImpExt parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp modifyImp(BidRequest bidRequest, Imp imp) {
        return imp.toBuilder()
                .bidfloor(resolveBidFloor(bidRequest, imp))
                .bidfloorcur(BIDDER_CURRENCY)
                .build();
    }

    private BigDecimal resolveBidFloor(BidRequest bidRequest, Imp imp) {
        final BigDecimal bidFloor = imp.getBidfloor();
        final String bidFloorCurrency = imp.getBidfloorcur();

        if (BidderUtil.isValidPrice(bidFloor)
                && StringUtils.isNotBlank(bidFloorCurrency)
                && !StringUtils.equalsIgnoreCase(bidFloorCurrency, BIDDER_CURRENCY)) {
            return currencyConversionService.convertCurrency(bidFloor, bidRequest, bidFloorCurrency, BIDDER_CURRENCY);
        }

        return bidFloor;
    }

    private ExtRequest modifyExtRequest(BidRequest request, DisplayioImpExt impExt) {
        final ExtRequest extRequest = request.getExt();
        final ExtRequest modifiedExtRequest = Optional.ofNullable(extRequest)
                .map(ext -> {
                    final ExtRequest copy = ExtRequest.of(extRequest.getPrebid());
                    copy.addProperties(extRequest.getProperties());
                    return copy;
                }).orElseGet(ExtRequest::empty);

        final DisplayioRequestExt requestExt = DisplayioRequestExt.of(impExt.getInventoryId(), impExt.getPlacementId());
        modifiedExtRequest.addProperty("displayio", mapper.mapper().valueToTree(requestExt));

        return modifiedExtRequest;
    }

    private static MultiMap makeHeaders() {
        return HttpUtil.headers().set(HttpUtil.X_OPENRTB_VERSION_HEADER, X_OPENRTB_VERSION);
    }

    private String resolveEndpoint(DisplayioImpExt impExt) {
        return endpointUrl
                .replace(PUBLISHER_ID_MACRO, HttpUtil.encodeUrl(StringUtils.defaultString(impExt.getPublisherId())));
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null
                || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                || bidResponse.getSeatbid().size() > 1) {

            throw new PreBidException("Invalid SeatBids count");
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> toBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid toBidderBid(Bid bid, String currency, List<BidderError> errors) {
        try {
            return BidderBid.of(bid, getBidType(bid.getMtype()), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType getBidType(Integer mType) {
        return switch (mType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case null, default -> throw new PreBidException("unsupported MType " + mType);
        };
    }
}
