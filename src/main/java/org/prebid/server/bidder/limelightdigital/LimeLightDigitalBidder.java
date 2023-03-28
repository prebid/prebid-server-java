package org.prebid.server.bidder.limelightdigital;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
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
import org.prebid.server.proto.openrtb.ext.request.limelightdigital.ExtImpLimeLightDigital;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class LimeLightDigitalBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpLimeLightDigital>> LIME_LIGHT_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String BIDDER_CURRENCY = "USD";
    private static final String URL_HOST_MACRO = "{{Host}}";
    private static final String URL_PUBLISHER_ID_MACRO = "{{PublisherID}}";

    private final String endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    public LimeLightDigitalBidder(String endpointUrl,
                                  CurrencyConversionService currencyConversionService,
                                  JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpLimeLightDigital extImpAdview = parseExtImp(imp);
                final String endpointUri = resolveEndpoint(extImpAdview);
                final Imp modifiedImp = modifyImp(imp, request);
                final BidRequest modifiedBidRequest = modifyRequest(request, modifiedImp);

                requests.add(createHttpRequest(modifiedBidRequest, endpointUri));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private ExtImpLimeLightDigital parseExtImp(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), LIME_LIGHT_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("ext.bidder is not provided");
        }
    }

    private String resolveEndpoint(ExtImpLimeLightDigital extImp) {
        final String host = extImp.getHost();
        final int firstDotIndex = StringUtils.indexOf(host, ".");
        if (firstDotIndex < 1 || firstDotIndex == StringUtils.length(host) - 1) {
            throw new PreBidException("Hostname is invalid: " + host);
        }

        final String publisherId = String.valueOf(extImp.getPublisherId());
        return endpointUrl.replace(URL_HOST_MACRO, HttpUtil.encodeUrl(host))
                .replace(URL_PUBLISHER_ID_MACRO, HttpUtil.encodeUrl(publisherId));
    }

    private Imp modifyImp(Imp imp, BidRequest request) {
        final Price bidFloorPrice = resolveBidFloor(imp, request);

        return imp.toBuilder()
                .bidfloor(bidFloorPrice.getValue())
                .bidfloorcur(bidFloorPrice.getCurrency())
                .ext(null)
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
                    .convertCurrency(bidFloorPrice.getValue(), bidRequest, bidFloorCur, BIDDER_CURRENCY);

            return Price.of(BIDDER_CURRENCY, convertedPrice);
        } catch (PreBidException e) {
            throw new PreBidException("Unable to convert provided bid floor currency from %s to %s for imp `%s`"
                    .formatted(bidFloorCur, BIDDER_CURRENCY, impId));
        }
    }

    private static BidRequest modifyRequest(BidRequest bidRequest, Imp imp) {
        return bidRequest.toBuilder()
                .id(bidRequest.getId() + "-" + imp.getId())
                .imp(Collections.singletonList(imp))
                .ext(null)
                .build();
    }

    private HttpRequest<BidRequest> createHttpRequest(BidRequest modifiedBidRequest, String endpointUri) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUri)
                .headers(HttpUtil.headers())
                .body(mapper.encodeToBytes(modifiedBidRequest))
                .payload(modifiedBidRequest)
                .build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest,
                                               BidResponse bidResponse,
                                               List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse, errors);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest,
                                                    BidResponse bidResponse,
                                                    List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> resolveBidderBid(bid, bidResponse.getCur(), bidRequest.getImp(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid resolveBidderBid(Bid bid, String currency, List<Imp> imps, List<BidderError> errors) {
        final BidType bidType;
        try {
            bidType = getBidType(bid.getImpid(), imps);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
        return BidderBid.of(bid, bidType, currency);
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                }
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                if (imp.getAudio() != null) {
                    return BidType.audio;
                }
                if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
                throw new PreBidException("Unknown media type of imp: '%s'".formatted(impId));
            }
        }
        throw new PreBidException("Bid contains unknown imp id: '%s'".formatted(impId));
    }
}
