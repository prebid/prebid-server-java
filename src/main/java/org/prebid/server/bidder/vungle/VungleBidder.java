package org.prebid.server.bidder.vungle;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
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
import org.prebid.server.bidder.vungle.model.VungleImpressionExt;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.vungle.ExtImpVungle;
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

public class VungleBidder implements Bidder<BidRequest> {

    private static final String BIDDER_CURRENCY = "USD";
    private static final String X_OPENRTB_VERSION = "2.5";

    private final String endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    public VungleBidder(String endpointUrl,
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
            try {
                final Price price = resolveBidFloor(imp, bidRequest);
                final VungleImpressionExt impExt = parseImpExt(imp);
                final VungleImpressionExt modifiedImpExt = modifyImpExt(impExt, bidRequest);
                final Imp modifiedImp = modifyImp(imp, modifiedImpExt, price);
                final BidRequest modifiedRequest = modifyBidRequest(
                        bidRequest,
                        modifiedImp,
                        modifiedImpExt.getBidder().getAppStoreId());

                httpRequests.add(makeHttpRequest(modifiedRequest));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
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

    private VungleImpressionExt parseImpExt(Imp imp) {
        return mapper.mapper().convertValue(imp.getExt(), VungleImpressionExt.class);
    }

    private static VungleImpressionExt modifyImpExt(VungleImpressionExt impExt, BidRequest bidRequest) {
        final ExtImpVungle bidder = impExt.getBidder();
        final String buyerId = ObjectUtil.getIfNotNull(bidRequest.getUser(), User::getBuyeruid);
        final ExtImpVungle vungle = ExtImpVungle.of(
                buyerId,
                bidder.getAppStoreId(),
                bidder.getPlacementReferenceId());

        return impExt.toBuilder().vungle(vungle).build();
    }

    private Imp modifyImp(Imp imp, VungleImpressionExt modifiedImpExt, Price price) {
        return imp.toBuilder()
                .tagid(modifiedImpExt.getBidder().getPlacementReferenceId())
                .ext(mapper.mapper().convertValue(modifiedImpExt, ObjectNode.class))
                .bidfloor(price.getValue() != null ? price.getValue() : imp.getBidfloor())
                .bidfloorcur(price.getValue() != null ? price.getCurrency() : imp.getBidfloorcur())
                .build();
    }

    private static BidRequest modifyBidRequest(BidRequest bidRequest, Imp imp, String appStoreId) {
        final App app = bidRequest.getApp();
        final Site site = bidRequest.getSite();
        if (app == null && site == null) {
            throw new PreBidException("The bid request must have an app or site object");
        }
        return bidRequest.toBuilder()
                .imp(Collections.singletonList(imp))
                .app(app == null ? App.builder().id(appStoreId).build() : app.toBuilder().id(appStoreId).build())
                .site(null)
                .build();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request) {
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
