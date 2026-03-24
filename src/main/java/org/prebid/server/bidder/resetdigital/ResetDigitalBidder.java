package org.prebid.server.bidder.resetdigital;

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
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class ResetDigitalBidder implements Bidder<BidRequest> {

    private static final String DEFAULT_CURRENCY = "USD";

    private final String endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    public ResetDigitalBidder(String endpointUrl,
                              CurrencyConversionService currencyConversionService,
                              JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> bannerImps = new ArrayList<>();
        final List<Imp> videoImps = new ArrayList<>();
        final List<Imp> audioImps = new ArrayList<>();
        Price bidFloorPrice;

        for (Imp imp : request.getImp()) {
            try {
                bidFloorPrice = resolveBidFloor(imp, request);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
            populateBannerImps(bannerImps, bidFloorPrice, imp);
            populateVideoImps(videoImps, bidFloorPrice, imp);
            populateAudiImps(audioImps, bidFloorPrice, imp);
        }

        return Result.withValues(getHttpRequests(request, bannerImps, videoImps, audioImps));
    }

    private List<HttpRequest<BidRequest>> getHttpRequests(BidRequest request,
                                                          List<Imp> bannerImps,
                                                          List<Imp> videoImps,
                                                          List<Imp> audioImps) {

        return Stream.of(bannerImps, videoImps, audioImps)
                .filter(CollectionUtils::isNotEmpty)
                .map(imp -> makeHttpRequest(request, imp))
                .toList();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest bidRequest, List<Imp> imp) {
        final BidRequest outgoingRequest = bidRequest.toBuilder().imp(imp).build();

        return BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper);
    }

    private static Imp modifyImp(Imp imp, Price bidFloorPrice) {
        return imp.toBuilder()
                .bidfloorcur(bidFloorPrice.getCurrency())
                .bidfloor(bidFloorPrice.getValue())
                .build();
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
                    .convertCurrency(bidFloorPrice.getValue(), bidRequest, bidFloorCur, DEFAULT_CURRENCY);

            return Price.of(DEFAULT_CURRENCY, convertedPrice);
        } catch (PreBidException e) {
            throw new PreBidException(
                    "Unable to convert provided bid floor currency from %s to %s for imp `%s`"
                            .formatted(bidFloorCur, DEFAULT_CURRENCY, impId));
        }
    }

    private static void populateBannerImps(List<Imp> bannerImps, Price bidFloorPrice, Imp imp) {
        if (imp.getBanner() != null) {
            final Imp bannerImp = imp.toBuilder().video(null).xNative(null).audio(null).build();
            bannerImps.add(modifyImp(bannerImp, bidFloorPrice));
        }
    }

    private static void populateVideoImps(List<Imp> videoImps, Price bidFloorPrice, Imp imp) {
        if (imp.getVideo() != null) {
            final Imp videoImp = imp.toBuilder().banner(null).xNative(null).audio(null).build();
            videoImps.add(modifyImp(videoImp, bidFloorPrice));
        }
    }

    private static void populateAudiImps(List<Imp> audioImps, Price bidFloorPrice, Imp imp) {
        if (imp.getAudio() != null) {
            final Imp audioImp = imp.toBuilder().banner(null).xNative(null).video(null).build();
            audioImps.add(modifyImp(audioImp, bidFloorPrice));
        }
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, httpCall.getRequest().getPayload()));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        if (bidResponse.getCur() != null && !StringUtils.equalsIgnoreCase(DEFAULT_CURRENCY, bidResponse.getCur())) {
            throw new PreBidException("Bidder support only USD currency");
        }
        return bidsFromResponse(bidResponse, bidRequest);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid, bidRequest.getImp()), DEFAULT_CURRENCY))
                .toList();
    }

    private static BidType getBidType(Bid bid, List<Imp> imps) {
        final String impId = bid.getImpid();
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getAudio() != null) {
                    return BidType.audio;
                }
            }
        }
        throw new PreBidException("Failed to find banner/video/audio impression " + impId);
    }
}
