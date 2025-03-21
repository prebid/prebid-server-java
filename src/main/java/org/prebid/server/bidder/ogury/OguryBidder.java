package org.prebid.server.bidder.ogury;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.json.JacksonMapper;
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

public class OguryBidder implements Bidder<BidRequest> {

    private static final String EXT_FIELD_BIDDER = "bidder";
    private static final String BIDDER_CURRENCY = "USD";
    private static final String PREBID_FIELD_ASSET_KEY = "assetKey";
    private static final String PREBID_FIELD_ADUNIT_ID = "adUnitId";

    private final String endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    public OguryBidder(String endpointUrl, CurrencyConversionService currencyConversionService, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();

        final List<Imp> modifiedImps = new ArrayList<>();
        final List<Imp> impsWithOguryParams = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final Imp modifiedImp = modifyImp(imp, bidRequest);

                modifiedImps.add(modifiedImp);
                if (hasOguryParams(imp)) {
                    impsWithOguryParams.add(modifiedImp);
                }
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (!isValidRequestKeys(bidRequest, impsWithOguryParams)) {
            errors.add(BidderError.badInput(
                    "Invalid request. assetKey/adUnitId or request.site.publisher.id required"));
            return Result.withErrors(errors);
        }

        final BidRequest modifiedBidRequest = bidRequest.toBuilder()
                .imp(CollectionUtils.isNotEmpty(impsWithOguryParams) ? impsWithOguryParams : modifiedImps)
                .build();

        final MultiMap headers = resolveHeaders(modifiedBidRequest.getDevice());
        final List<HttpRequest<BidRequest>> httpRequests = Collections.singletonList(
                BidderUtil.defaultRequest(modifiedBidRequest, headers, endpointUrl, mapper));

        return Result.of(httpRequests, errors);
    }

    private ObjectNode resolveImpExtBidderHoist(Imp imp) {
        return (ObjectNode) imp.getExt().get(EXT_FIELD_BIDDER);
    }

    private Imp modifyImp(Imp imp, BidRequest bidRequest) {
        final Price price = resolvePrice(imp, bidRequest);
        return imp.toBuilder()
                .tagid(imp.getId())
                .bidfloor(price.getValue())
                .bidfloorcur(price.getCurrency())
                .ext(modifyExt(imp))
                .build();
    }

    private Price resolvePrice(Imp imp, BidRequest bidRequest) {
        final Price initialBidFloorPrice = Price.of(imp.getBidfloorcur(), imp.getBidfloor());
        return BidderUtil.shouldConvertBidFloor(initialBidFloorPrice, BIDDER_CURRENCY)
                ? convertBidFloor(initialBidFloorPrice, bidRequest)
                : initialBidFloorPrice;
    }

    private Price convertBidFloor(Price bidFloorPrice, BidRequest bidRequest) {
        final BigDecimal convertedPrice = currencyConversionService.convertCurrency(
                bidFloorPrice.getValue(),
                bidRequest,
                bidFloorPrice.getCurrency(),
                BIDDER_CURRENCY);

        return Price.of(BIDDER_CURRENCY, convertedPrice);
    }

    private ObjectNode modifyExt(Imp imp) {
        final ObjectNode impExt = imp.getExt();
        final ObjectNode impExtBidderHoist = resolveImpExtBidderHoist(imp);

        final ObjectNode modifiedImpExt = impExt.deepCopy();
        modifiedImpExt.setAll(impExtBidderHoist);
        modifiedImpExt.remove(EXT_FIELD_BIDDER);

        return modifiedImpExt;
    }

    private boolean hasOguryParams(Imp imp) {
        final ObjectNode impExtBidderHoist = resolveImpExtBidderHoist(imp);

        return impExtBidderHoist != null
                && impExtBidderHoist.has(PREBID_FIELD_ASSET_KEY)
                && impExtBidderHoist.has(PREBID_FIELD_ADUNIT_ID);
    }

    private boolean isValidRequestKeys(BidRequest request, List<Imp> impsWithOguryParams) {
        return !CollectionUtils.isEmpty(impsWithOguryParams) || Optional.ofNullable(request.getSite())
                .map(Site::getPublisher)
                .map(Publisher::getId)
                .isPresent();
    }

    private MultiMap resolveHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers();

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER, device.getLanguage());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final String body = httpCall.getResponse().getBody();

            final BidResponse bidResponse = mapper.decodeValue(body, BidResponse.class);

            final List<BidderError> errors = new ArrayList<>();
            final List<BidderBid> bidderBids = extractBids(bidResponse, errors);

            return Result.of(bidderBids, errors);
        } catch (Exception e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        return Optional.ofNullable(bidResponse)
                .map(BidResponse::getSeatbid)
                .stream()
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> createBidderBid(bid, bidResponse, errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid createBidderBid(Bid bid, BidResponse bidResponse, List<BidderError> errors) {
        try {
            return BidderBid.of(bid, getBidType(bid), bidResponse.getCur());
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType getBidType(Bid bid) {
        final Integer markupType = bid.getMtype();
        if (markupType == null) {
            throw new PreBidException("Missing MType for impression: `%s`".formatted(bid.getImpid()));
        }

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException(
                    "Unsupported MType '%d', for impression '%s'".formatted(markupType, bid.getImpid()));
        };
    }
}
