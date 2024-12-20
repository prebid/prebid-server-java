package org.prebid.server.bidder.insticator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.insticator.ExtImpInsticator;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class InsticatorBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpInsticator>> TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final String DEFAULT_BIDDER_CURRENCY = "USD";
    private static final String INSTICATOR_FIELD = "insticator";
    private static final InsticatorExtRequestCaller DEFAULT_INSTICATOR_CALLER =
            InsticatorExtRequestCaller.of("Prebid-Server", "n/a");

    private final CurrencyConversionService currencyConversionService;
    private final String endpointUrl;
    private final JacksonMapper mapper;

    public InsticatorBidder(CurrencyConversionService currencyConversionService,
                            String endpointUrl,
                            JacksonMapper mapper) {

        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final Map<String, List<Imp>> groupedImps = new HashMap<>();
        final List<BidderError> errors = new ArrayList<>();

        String publisherId = null;

        for (Imp imp : request.getImp()) {
            try {
                validateImp(imp);
                final ExtImpInsticator extImp = parseImpExt(imp);

                if (publisherId == null) {
                    publisherId = extImp.getPublisherId();
                }

                final Imp modifiedImp = modifyImp(request, imp, extImp);
                groupedImps.computeIfAbsent(extImp.getAdUnitId(), key -> new ArrayList<>()).add(modifiedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final BidRequest modifiedRequest = modifyRequest(request, publisherId, errors);
        final List<HttpRequest<BidRequest>> requests = groupedImps.values().stream()
                .map(imps -> modifiedRequest.toBuilder().imp(imps).build())
                .map(finalRequest -> BidderUtil.defaultRequest(
                        finalRequest,
                        makeHeaders(finalRequest.getDevice()),
                        endpointUrl,
                        mapper))
                .toList();

        return Result.of(requests, errors);
    }

    private void validateImp(Imp imp) {
        final Video video = imp.getVideo();
        if (video == null) {
            return;
        }

        if (isInvalidDimension(video.getH())
                || isInvalidDimension(video.getW())
                || CollectionUtils.isNotEmpty(video.getMimes())) {

            throw new PreBidException("One or more invalid or missing video field(s) w, h, mimes");
        }
    }

    private static boolean isInvalidDimension(Integer dimension) {
        return dimension == null || dimension == 0;
    }

    private ExtImpInsticator parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp modifyImp(BidRequest request, Imp imp, ExtImpInsticator extImp) {
        final Price bidFloorPrice = resolveBidFloor(request, imp);
        return imp.toBuilder()
                .ext(mapper.mapper().createObjectNode().set(INSTICATOR_FIELD, mapper.mapper().valueToTree(extImp)))
                .bidfloorcur(bidFloorPrice.getCurrency())
                .bidfloor(bidFloorPrice.getValue())
                .build();
    }

    private Price resolveBidFloor(BidRequest bidRequest, Imp imp) {
        final Price initialBidFloorPrice = Price.of(imp.getBidfloorcur(), imp.getBidfloor());
        return BidderUtil.isValidPrice(initialBidFloorPrice)
                ? convertBidFloor(initialBidFloorPrice, bidRequest)
                : initialBidFloorPrice;
    }

    private Price convertBidFloor(Price bidFloorPrice, BidRequest bidRequest) {
        final BigDecimal convertedPrice = currencyConversionService.convertCurrency(
                bidFloorPrice.getValue(),
                bidRequest,
                bidFloorPrice.getCurrency(),
                DEFAULT_BIDDER_CURRENCY);

        return Price.of(DEFAULT_BIDDER_CURRENCY, BidderUtil.roundFloor(convertedPrice));
    }

    private BidRequest modifyRequest(BidRequest request, String publisherId, List<BidderError> errors) {
        return request.toBuilder()
                .site(modifySite(request.getSite(), publisherId))
                .app(modifyApp(request.getApp(), publisherId))
                .ext(modifyExtRequest(request.getExt(), errors))
                .build();
    }

    private static Site modifySite(Site site, String id) {
        return Optional.ofNullable(site)
                .map(Site::toBuilder)
                .map(builder -> builder.publisher(modifyPublisher(site.getPublisher(), id)))
                .map(Site.SiteBuilder::build)
                .orElse(null);
    }

    private static App modifyApp(App app, String id) {
        return Optional.ofNullable(app)
                .map(App::toBuilder)
                .map(builder -> builder.publisher(modifyPublisher(app.getPublisher(), id)))
                .map(App.AppBuilder::build)
                .orElse(null);
    }

    private static Publisher modifyPublisher(Publisher publisher, String id) {
        return Optional.ofNullable(publisher)
                .map(Publisher::toBuilder)
                .orElseGet(Publisher::builder)
                .id(id)
                .build();
    }

    private ExtRequest modifyExtRequest(ExtRequest extRequest, List<BidderError> errors) {
        final ExtRequest modifiedExtRequest = extRequest == null ? ExtRequest.empty() : extRequest;
        final InsticatorExtRequest existingInsticator;

        try {
            existingInsticator = mapper.mapper().convertValue(
                    modifiedExtRequest.getProperty(INSTICATOR_FIELD),
                    InsticatorExtRequest.class);
        } catch (IllegalArgumentException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return modifiedExtRequest;
        }

        modifiedExtRequest.addProperty(
                INSTICATOR_FIELD,
                mapper.mapper().valueToTree(buildInsticator(existingInsticator)));

        return modifiedExtRequest;
    }

    private static InsticatorExtRequest buildInsticator(InsticatorExtRequest existingInsticator) {
        if (existingInsticator == null || CollectionUtils.isEmpty(existingInsticator.getCaller())) {
            return InsticatorExtRequest.of(Collections.singletonList(DEFAULT_INSTICATOR_CALLER));
        }

        final List<InsticatorExtRequestCaller> callers = new ArrayList<>(existingInsticator.getCaller());
        callers.add(DEFAULT_INSTICATOR_CALLER);
        return InsticatorExtRequest.of(callers);
    }

    private static MultiMap makeHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers();
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER,
                ObjectUtil.getIfNotNull(device, Device::getUa));
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER,
                ObjectUtil.getIfNotNull(device, Device::getIp));
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, "IP",
                ObjectUtil.getIfNotNull(device, Device::getIp));
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER,
                ObjectUtil.getIfNotNull(device, Device::getIpv6));

        return headers;
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
                .map(bid -> BidderBid.of(bid, getBidType(bid), bidResponse.getCur()))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidType getBidType(Bid bid) {
        return switch (bid.getMtype()) {
            case 2 -> BidType.video;
            case null, default -> BidType.banner;
        };
    }
}
