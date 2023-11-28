package org.prebid.server.bidder.liftoff;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.liftoff.model.LiftoffImpressionExt;
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
import org.prebid.server.proto.openrtb.ext.request.liftoff.ExtImpLiftoff;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class LiftoffBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    private static final String BIDDER_CURRENCY = "USD";
    private static final String X_OPENRTB_VERSION = "2.5";

    public LiftoffBidder(String endpointUrl,
                         CurrencyConversionService currencyConversionService,
                         JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            final Imp.ImpBuilder impBuilder = imp.toBuilder();
            final Price price;
            final ObjectNode convertedExtImpLiftoff;
            final ExtImpLiftoff liftoffImpressionExtBidder;
            try {
                price = resolveBidFloor(imp, bidRequest);
                final LiftoffImpressionExt liftoffImpressionExt = resolveLiftoffImpressionExt(imp);
                liftoffImpressionExtBidder = liftoffImpressionExt.getBidder();
                convertedExtImpLiftoff = resolveExtImpLiftoff(
                        resolveLiftoffImpExt(bidRequest, liftoffImpressionExtBidder, liftoffImpressionExt));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }

            httpRequests.add(createRequest(
                    prepareBidRequest(bidRequest,
                            prepareImp(impBuilder, liftoffImpressionExtBidder, convertedExtImpLiftoff, price, imp),
                            liftoffImpressionExtBidder)));
        }

        return Result.of(httpRequests, errors);
    }

    private static LiftoffImpressionExt resolveLiftoffImpExt(BidRequest bidRequest,
                                                             ExtImpLiftoff liftoffImpressionExtBidder,
                                                             LiftoffImpressionExt liftoffImpressionExt) {
        return liftoffImpressionExt.toBuilder()
                .vungle(createExtImpLiftoff(
                        ObjectUtil.getIfNotNull(bidRequest.getUser(), User::getBuyeruid), liftoffImpressionExtBidder))
                .build();
    }

    private Price resolveBidFloor(Imp imp, BidRequest bidRequest) {
        BigDecimal bigDecimal = null;
        if (BidderUtil.isValidPrice(imp.getBidfloor())
                && !StringUtils.equalsIgnoreCase(imp.getBidfloorcur(), BIDDER_CURRENCY)
                && StringUtils.isNotBlank(imp.getBidfloorcur())) {
            bigDecimal = currencyConversionService.convertCurrency(
                    imp.getBidfloor(), bidRequest, imp.getBidfloorcur(), BIDDER_CURRENCY);
        }

        return Price.of(BIDDER_CURRENCY, bigDecimal);
    }

    private ObjectNode resolveExtImpLiftoff(LiftoffImpressionExt impressionExt) {
        return mapper.mapper().convertValue(impressionExt, ObjectNode.class);
    }

    private LiftoffImpressionExt resolveLiftoffImpressionExt(Imp imp) {
        return mapper.mapper().convertValue(imp.getExt(), LiftoffImpressionExt.class);
    }

    private static Imp prepareImp(Imp.ImpBuilder impBuilder,
                                  ExtImpLiftoff extImpLiftoff,
                                  ObjectNode convertedExtImpLiftoff,
                                  Price price,
                                  Imp imp) {
        return impBuilder
                .tagid(extImpLiftoff.getPlacementReferenceId())
                .ext(convertedExtImpLiftoff)
                .bidfloor(price.getValue() != null ? price.getValue() : imp.getBidfloor())
                .bidfloorcur(price.getValue() != null ? price.getCurrency() : imp.getBidfloorcur())
                .build();
    }

    private static ExtImpLiftoff createExtImpLiftoff(String buyeruid, ExtImpLiftoff extImpLiftoff) {
        return ExtImpLiftoff.of(buyeruid, extImpLiftoff.getAppStoreId(), extImpLiftoff.getPlacementReferenceId());
    }

    private static BidRequest prepareBidRequest(BidRequest bidRequest, Imp imp, ExtImpLiftoff extImpLiftoff) {
        final App app = ObjectUtil.getIfNotNull(bidRequest, BidRequest::getApp);
        return bidRequest.toBuilder()
                .imp(Collections.singletonList(imp))
                .app(app != null ? app.toBuilder().id(extImpLiftoff.getAppStoreId()).build() : null)
                .build();
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .impIds(BidderUtil.impIds(request))
                .headers(headers())
                .payload(request)
                .body(mapper.encodeToBytes(request))
                .build();
    }

    private static MultiMap headers() {
        return HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, X_OPENRTB_VERSION);
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
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
        return bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.video, bidResponse.getCur()))
                .toList();
    }
}
