package org.prebid.server.bidder.pubnative;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.pubnative.ExtImpPubnative;
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

public class PubnativeBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpPubnative>> PUBNATIVE_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String PUBNATIVE_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final CurrencyConversionService currencyConversionService;

    public PubnativeBidder(String endpointUrl,
                           JacksonMapper mapper,
                           CurrencyConversionService currencyConversionService) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final Device device = bidRequest.getDevice();
        if (device == null || StringUtils.isBlank(device.getOs())) {
            return Result.withError(BidderError.badInput("Impression is missing device OS information"));
        }

        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();
        for (Imp imp : bidRequest.getImp()) {
            try {
                validateImp(imp);
                final ExtImpPubnative extImpPubnative = parseImpExt(imp.getExt());
                final BidRequest outgoingRequest = createRequest(bidRequest, imp);
                httpRequests.add(createHttpRequest(outgoingRequest, extImpPubnative));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    private static void validateImp(Imp imp) {
        if (imp.getBanner() == null && imp.getVideo() == null && imp.getXNative() == null) {
            throw new PreBidException("Pubnative only supports banner, video or native ads.");
        }
    }

    private ExtImpPubnative parseImpExt(ObjectNode impExt) {
        try {
            return mapper.mapper().convertValue(impExt, PUBNATIVE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private BidRequest createRequest(BidRequest bidRequest, Imp imp) {
        return bidRequest.toBuilder()
                .cur(Collections.singletonList(PUBNATIVE_CURRENCY))
                .imp(Collections.singletonList(resolveImp(bidRequest, imp)))
                .build();
    }

    private Imp resolveImp(BidRequest bidRequest, Imp imp) {
        final Banner resolvedBanner = resolveBanner(imp.getBanner());
        final BigDecimal resolvedBidFloor = resolveBidFloor(bidRequest, imp);
        return resolvedBanner == null && resolvedBidFloor == null
                ? imp
                : imp.toBuilder()
                .banner(ObjectUtils.defaultIfNull(resolvedBanner, imp.getBanner()))
                .bidfloor(ObjectUtils.defaultIfNull(resolvedBidFloor, imp.getBidfloor()))
                .bidfloorcur(resolvedBidFloor == null ? imp.getBidfloorcur() : PUBNATIVE_CURRENCY)
                .build();
    }

    private static Banner resolveBanner(Banner banner) {
        if (banner != null) {
            final Integer width = banner.getW();
            final Integer height = banner.getH();
            if (width == null || width == 0 || height == null || height == 0) {
                final List<Format> formats = banner.getFormat();
                if (CollectionUtils.isEmpty(formats)) {
                    throw new PreBidException("Size information missing for banner");
                }

                final Format firstFormat = formats.get(0);
                return banner.toBuilder()
                        .w(firstFormat.getW())
                        .h(firstFormat.getH())
                        .build();
            }
        }
        return null;
    }

    private BigDecimal resolveBidFloor(BidRequest bidRequest, Imp imp) {
        final BigDecimal bidFloor = imp.getBidfloor();
        final String bidFloorCur = resolveBidFloorCurrency(bidRequest, imp.getBidfloorcur());
        if (!BidderUtil.isValidPrice(bidFloor)
                || StringUtils.equals(bidFloorCur, PUBNATIVE_CURRENCY)
                || StringUtils.isEmpty(bidFloorCur)) {
            return null;
        }

        return currencyConversionService.convertCurrency(bidFloor, bidRequest, bidFloorCur, PUBNATIVE_CURRENCY);
    }

    private static String resolveBidFloorCurrency(BidRequest bidRequest, String bidFloorCurrency) {
        if (StringUtils.isNotEmpty(bidFloorCurrency)) {
            return bidFloorCurrency;
        }
        final List<String> bidRequestCurrencies = bidRequest.getCur();
        return CollectionUtils.isNotEmpty(bidRequestCurrencies) ? bidRequestCurrencies.get(0) : null;
    }

    private HttpRequest<BidRequest> createHttpRequest(BidRequest outgoingRequest, ExtImpPubnative impExt) {
        final String requestUri = String.format("%s?apptoken=%s&zoneid=%s", endpointUrl, impExt.getAppAuthToken(),
                impExt.getZoneId());

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(requestUri)
                .headers(HttpUtil.headers())
                .body(mapper.encodeToBytes(outgoingRequest))
                .payload(outgoingRequest)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, httpCall.getRequest().getPayload()), Collections.emptyList());
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse.getSeatbid(), bidRequest.getImp(), bidResponse.getCur());
    }

    private static List<BidderBid> bidsFromResponse(List<SeatBid> seatbid, List<Imp> imps, String currency) {
        return seatbid.stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> createBidderBid(imps, currency, bid))
                .collect(Collectors.toList());
    }

    private static BidderBid createBidderBid(List<Imp> imps, String currency, Bid bid) {
        final Imp imp = findImpById(bid.getImpid(), imps);
        return BidderBid.of(updateBidWithSize(bid, imp), resolveBidType(imp), currency);
    }

    private static Bid updateBidWithSize(Bid bid, Imp imp) {
        if (bid.getW() != null && bid.getH() != null) {
            return bid;
        }

        final Format format = imp != null && imp.getBanner() != null
                ? resolveBidSizeFromBanner(imp.getBanner())
                : null;
        return format != null
                ? bid.toBuilder().w(format.getW()).h(format.getH()).build()
                : bid;
    }

    private static Imp findImpById(String impId, List<Imp> imps) {
        return imps.stream()
                .filter(imp -> imp.getId().equals(impId))
                .findFirst()
                .orElse(null);
    }

    private static BidType resolveBidType(Imp imp) {
        if (imp == null) {
            return BidType.banner;
        } else if (imp.getVideo() != null) {
            return BidType.video;
        } else if (imp.getXNative() != null) {
            return BidType.xNative;
        } else {
            return BidType.banner;
        }
    }

    private static Format resolveBidSizeFromBanner(Banner banner) {
        Format result = null;
        final Integer width = banner.getW();
        final Integer height = banner.getH();

        final List<Format> formats = banner.getFormat();
        if (width != null && height != null) {
            result = isOnlyOneSize(width, height, formats)
                    ? Format.builder().w(width).h(height).build()
                    : null;
        } else if (formats.size() == 1) {
            result = formats.get(0);
        }
        return result;
    }

    private static boolean isOnlyOneSize(Integer width, Integer height, List<Format> formats) {
        return CollectionUtils.isEmpty(formats) || (formats.size() == 1 && isSameFormat(width, height, formats.get(0)));
    }

    private static boolean isSameFormat(Integer width, Integer height, Format format) {
        return width.equals(format.getW()) && height.equals(format.getH());
    }
}
