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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

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
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        List<Imp> modifiedImps = new ArrayList<>();
        final List<Imp> impsWithOguryParams = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final ObjectNode impExt = resolveImpExt(imp);
                final ObjectNode impExtBidderHoist = resolveImpExtBidderHoist(impExt);

                final ObjectNode modifiedImpExt = modifyImpExt(impExt, impExtBidderHoist);
                final BigDecimal bidFloor = resolveBidFloor(bidRequest, imp);
                final Imp modifiedImp = modifyImp(imp, bidFloor, modifiedImpExt);
                modifiedImps.add(modifiedImp);

                if (hasOguryParams(impExtBidderHoist)) {
                    impsWithOguryParams.add(modifiedImp);
                }
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final BidderError error = validateRequestKeys(bidRequest, impsWithOguryParams);
        if (error != null) {
            errors.add(error);
            return Result.withErrors(errors);
        }

        if (CollectionUtils.isNotEmpty(impsWithOguryParams)) {
            modifiedImps = impsWithOguryParams;
        }

        final BidRequest modifiedBidRequest = bidRequest.toBuilder().imp(modifiedImps).build();
        final MultiMap headers = buildHeaders(modifiedBidRequest);
        httpRequests.add(BidderUtil.defaultRequest(modifiedBidRequest, headers, endpointUrl, mapper));

        return Result.of(httpRequests, errors);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final HttpResponse response = getResponse(httpCall);
        if (response == null || isNotHasContent(response)) {
            return Result.empty();
        }

        final BidderError error = checkResponseStatusCodeForErrors(response);
        if (error != null) {
            return Result.withError(error);
        }

        try {
            final String body = response.getBody();
            if (StringUtils.isEmpty(body)) {
                return Result.empty();
            }

            final BidResponse bidResponse = mapper.decodeValue(body, BidResponse.class);

            final List<BidderError> errors = new ArrayList<>();
            final List<BidderBid> bidderBids = extractBids(bidResponse, errors::add);

            return Result.of(bidderBids, errors);
        } catch (Exception e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private Imp modifyImp(Imp imp, BigDecimal bidFloor, ObjectNode modifiedImpExt) {
        return imp.toBuilder()
                .tagid(imp.getId())
                .bidfloor(bidFloor)
                .bidfloorcur(BIDDER_CURRENCY)
                .ext(modifiedImpExt)
                .build();
    }

    private ObjectNode modifyImpExt(ObjectNode impExt, ObjectNode impExtBidderHoist) {
        if (impExt == null || impExtBidderHoist == null) {
            return impExt;
        }

        final ObjectNode modifiedImpExt = impExt.deepCopy();
        Optional.ofNullable(impExtBidderHoist.fieldNames())
                .ifPresent(fields -> {
                    fields.forEachRemaining(field -> modifiedImpExt.set(field, impExtBidderHoist.get(field)));
                    modifiedImpExt.remove(EXT_FIELD_BIDDER);
                });

        return modifiedImpExt;
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, Consumer<BidderError> bidErrorHandler) {
        return Optional.ofNullable(bidResponse.getSeatbid()).stream()
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> {
                    try {
                        return BidderBid.of(bid, getBidType(bid), bidResponse.getCur());
                    } catch (PreBidException e) {
                        bidErrorHandler.accept(BidderError.badServerResponse(e.getMessage()));
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private HttpResponse getResponse(BidderCall<BidRequest> httpCall) {
        return Optional.ofNullable(httpCall)
                .map(BidderCall::getResponse)
                .orElse(null);
    }

    private boolean hasOguryParams(ObjectNode impExtBidderHoist) {
        return Optional.ofNullable(impExtBidderHoist).map(it -> it.get(PREBID_FIELD_ASSET_KEY) != null
                        && it.get(PREBID_FIELD_ADUNIT_ID) != null)
                .orElse(false);
    }

    private ObjectNode resolveImpExtBidderHoist(ObjectNode impExt) {
        return (ObjectNode) Optional.ofNullable(impExt)
                .map(ext -> ext.get(EXT_FIELD_BIDDER))
                .orElse(null);
    }

    private ObjectNode resolveImpExt(Imp imp) {
        return Optional.of(imp).map(Imp::getExt).orElse(null);
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
                    "Unsupported MType '%d', for impression '%s'".formatted(bid.getMtype(), bid.getImpid()));
        };
    }

    private BidderError validateRequestKeys(BidRequest request, List<Imp> impsWithOguryParams) {
        final Optional<Site> siteOpt = Optional.of(request).map(BidRequest::getSite);
        final Optional<String> publisherId = siteOpt.map(Site::getPublisher).map(Publisher::getId);

        if (CollectionUtils.isEmpty(impsWithOguryParams) && (siteOpt.isEmpty() || publisherId.isEmpty())) {
            return BidderError.badInput("Invalid request. assetKey/adUnitId or request.site.publisher.id required");
        }

        return null;
    }

    private BidderError checkResponseStatusCodeForErrors(HttpResponse response) {
        final int statusCode = response.getStatusCode();

        if (statusCode == HttpResponseStatus.BAD_REQUEST.code()) {
            return BidderError.badInput("Unexpected status code: %d. Run with request.debug = 1 for more info"
                    .formatted(statusCode));
        }

        if (statusCode != HttpResponseStatus.OK.code()) {
            return BidderError.generic("Unexpected status code: %d. Run with request.debug = 1 for more info"
                    .formatted(statusCode));
        }

        return null;
    }

    private boolean isNotHasContent(HttpResponse response) {
        return Optional.of(response)
                .map(HttpResponse::getStatusCode)
                .map(code -> code == HttpResponseStatus.NO_CONTENT.code() || StringUtils.isEmpty(response.getBody()))
                .orElse(false);
    }

    private MultiMap buildHeaders(BidRequest request) {
        final MultiMap headers = HttpUtil.headers();

        Optional.ofNullable(request)
                .map(BidRequest::getDevice)
                .ifPresentOrElse(device -> {
                    final String lang = device.getLanguage();
                    headers.add(HttpUtil.USER_AGENT_HEADER, device.getUa())
                            .add(HttpUtil.ACCEPT_LANGUAGE_HEADER, lang != null ? lang : "en-US");

                    Optional.of(device)
                            .map(Device::getIp)
                            .ifPresent(ip -> headers.add(HttpUtil.X_FORWARDED_FOR_HEADER, ip));

                    Optional.of(device)
                            .map(Device::getIpv6)
                            .ifPresent(ip -> headers.add(HttpUtil.X_FORWARDED_FOR_HEADER, ip));
                }, () -> headers.add(HttpUtil.ACCEPT_LANGUAGE_HEADER, "en-US"));

        return headers;
    }
}
