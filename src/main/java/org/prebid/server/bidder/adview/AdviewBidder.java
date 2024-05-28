package org.prebid.server.bidder.adview;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
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
import org.prebid.server.proto.openrtb.ext.request.adview.ExtImpAdview;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AdviewBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdview>> ADVIEW_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String ACCOUNT_ID_MACRO = "{{AccountId}}";
    private static final String BIDDER_CURRENCY = "USD";

    private final String endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    public AdviewBidder(String endpointUrl,
                        CurrencyConversionService currencyConversionService,
                        JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp: request.getImp()) {
            try {
                final ExtImpAdview extImp = parseExtImp(imp);
                final Price bidFloorPrice = resolveBidFloor(imp, request);
                final Imp modifiedImp = modifyImp(imp, extImp.getMasterTagId(), bidFloorPrice);
                final BidRequest modifiedRequest = modifyRequest(request, modifiedImp);
                final String resolvedUrl = resolveEndpoint(extImp.getAccountId());
                httpRequests.add(BidderUtil.defaultRequest(modifiedRequest, resolvedUrl, mapper));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpAdview parseExtImp(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADVIEW_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("invalid imp.ext");
        }
    }

    private Price resolveBidFloor(Imp imp, BidRequest bidRequest) {
        final Price initialBidFloorPrice = Price.of(imp.getBidfloorcur(), imp.getBidfloor());
        return BidderUtil.isValidPrice(initialBidFloorPrice)
                ? convertBidFloor(initialBidFloorPrice, imp.getId(), bidRequest)
                : initialBidFloorPrice;
    }

    private Price convertBidFloor(Price bidFloorPrice, String impId, BidRequest bidRequest) {
        final String bidFloorCur = bidFloorPrice.getCurrency();
        try {
            final BigDecimal convertedPrice = currencyConversionService
                    .convertCurrency(bidFloorPrice.getValue(), bidRequest, bidFloorCur, BIDDER_CURRENCY);

            return Price.of(BIDDER_CURRENCY, convertedPrice);
        } catch (PreBidException e) {
            throw new PreBidException("Unable to convert provided bid floor currency from %s to %s for imp `%s`"
                    .formatted(bidFloorCur, BIDDER_CURRENCY, impId));
        }
    }

    private static BidRequest modifyRequest(BidRequest bidRequest, Imp modifiedImp) {
        return bidRequest.toBuilder()
                .imp(Collections.singletonList(modifiedImp))
                .cur(Collections.singletonList(BIDDER_CURRENCY))
                .build();
    }

    private static Imp modifyImp(Imp imp, String masterTagId, Price bidFloorPrice) {
        return imp.toBuilder()
                .tagid(masterTagId)
                .bidfloor(bidFloorPrice.getValue())
                .bidfloorcur(bidFloorPrice.getCurrency())
                .banner(resolveBanner(imp.getBanner()))
                .build();
    }

    private static Banner resolveBanner(Banner banner) {
        final List<Format> formats = banner != null ? banner.getFormat() : null;
        if (CollectionUtils.isNotEmpty(formats)) {
            final Format firstFormat = formats.get(0);
            return firstFormat != null
                    ? banner.toBuilder().w(firstFormat.getW()).h(firstFormat.getH()).build()
                    : banner;
        }
        return banner;
    }

    private String resolveEndpoint(String accountId) {
        return endpointUrl.replace(ACCOUNT_ID_MACRO, HttpUtil.encodeUrl(accountId));
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
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
                .map(bid -> makeBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBid(Bid bid, String currency, List<BidderError> errors) {
        try {
            final BidType mediaType = getBidMediaType(bid);
            return BidderBid.of(bid, mediaType, currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }

    }

    private static BidType getBidMediaType(Bid bid) {
        final Integer markupType = bid.getMtype();
        if (markupType == null) {
            throw new PreBidException("Missing MType for bid: " + bid.getId());
        }

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException(
                    "Unable to fetch mediaType " + bid.getMtype() + " in multi-format: " + bid.getImpid());
        };
    }
}
