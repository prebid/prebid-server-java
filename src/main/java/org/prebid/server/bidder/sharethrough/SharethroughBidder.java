package org.prebid.server.bidder.sharethrough;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.sharethrough.ExtImpSharethrough;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SharethroughBidder implements Bidder<BidRequest> {

    private static final String ADAPTER_VERSION = "10.0";
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final BidType DEFAULT_BID_TYPE = BidType.banner;

    private static final TypeReference<ExtPrebid<?, ExtImpSharethrough>> SHARETHROUGH_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final PrebidVersionProvider prebidVersionProvider;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    public SharethroughBidder(String endpointUrl,
                              CurrencyConversionService currencyConversionService,
                              PrebidVersionProvider prebidVersionProvider,
                              JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.prebidVersionProvider = Objects.requireNonNull(prebidVersionProvider);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        BidRequest modifiedBidRequest = null;

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpSharethrough extImpSharethrough = parseImpExt(imp);
                final Price bidFloorPrice = resolveBidFloor(imp, request);
                modifiedBidRequest = modifyRequest(imp, request, extImpSharethrough, bidFloorPrice);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }

            httpRequests.add(makeHttpRequest(modifiedBidRequest));
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpSharethrough parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), SHARETHROUGH_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Price resolveBidFloor(Imp imp, BidRequest bidRequest) {
        final Price initialBidFloorPrice = Price.of(imp.getBidfloorcur(), imp.getBidfloor());
        return BidderUtil.isValidPrice(initialBidFloorPrice)
                ? convertBidFloor(initialBidFloorPrice, bidRequest)
                : initialBidFloorPrice;
    }

    private Price convertBidFloor(Price bidFloorPrice, BidRequest bidRequest) {
        try {
            final BigDecimal convertedPrice = currencyConversionService
                    .convertCurrency(bidFloorPrice.getValue(),
                            bidRequest,
                            bidFloorPrice.getCurrency(),
                            DEFAULT_BID_CURRENCY);

            return Price.of(DEFAULT_BID_CURRENCY, convertedPrice);
        } catch (PreBidException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private BidRequest modifyRequest(Imp imp,
                                     BidRequest bidRequest,
                                     ExtImpSharethrough extImpSharethrough,
                                     Price bidFloorPrice) {

        return bidRequest.toBuilder()
                .bcat(updateBcat(bidRequest, extImpSharethrough))
                .badv(updateBadv(bidRequest, extImpSharethrough))
                .imp(Collections.singletonList(modifyImp(imp, extImpSharethrough.getPkey(), bidFloorPrice)))
                .source(updateSource(bidRequest.getSource()))
                .build();
    }

    private static List<String> updateBadv(BidRequest bidRequest, ExtImpSharethrough extImpSharethrough) {
        final List<String> extImpSharethroughBadv = extImpSharethrough.getBadv();
        final List<String> bidRequestBadv = bidRequest.getBadv();

        return bidRequestBadv != null
                ? ListUtils.union(extImpSharethroughBadv, bidRequestBadv)
                : extImpSharethroughBadv;
    }

    private static List<String> updateBcat(BidRequest bidRequest, ExtImpSharethrough extImpSharethrough) {
        final List<String> extImpSharethroughBcat = extImpSharethrough.getBcat();
        final List<String> requestBcat = bidRequest.getBcat();

        return requestBcat != null
                ? ListUtils.union(extImpSharethroughBcat, requestBcat)
                : extImpSharethroughBcat;
    }

    private static Imp modifyImp(Imp imp, String pKey, Price bidFloorPrice) {
        return imp.toBuilder()
                .tagid(pKey)
                .bidfloor(bidFloorPrice.getValue())
                .bidfloorcur(bidFloorPrice.getCurrency())
                .build();
    }

    private Source updateSource(Source source) {
        return source != null
                ? source.toBuilder().ext(createExtSource()).build()
                : Source.builder().ext(createExtSource()).build();
    }

    private ExtSource createExtSource() {
        final ExtSource extSource = ExtSource.of(null);
        extSource.addProperty("str", TextNode.valueOf(ADAPTER_VERSION));
        extSource.addProperty("version", TextNode.valueOf(prebidVersionProvider.getNameVersionRecord()));
        return extSource;
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .payload(request)
                .body(mapper.encodeToBytes(request))
                .build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final BidRequest request = httpCall.getRequest().getPayload();
            return Result.withValues(extractBids(bidResponse, request));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, bidRequest);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, resolvedBidType(bidRequest), DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    private static BidType resolvedBidType(BidRequest bidRequest) {
        return bidRequest.getImp().get(0).getVideo() != null ? BidType.video : DEFAULT_BID_TYPE;
    }
}
