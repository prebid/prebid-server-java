package org.prebid.server.bidder.bidstack;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.bidstack.ExtImpBidstack;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class BidstackBidder implements Bidder<BidRequest> {

    private static final String BIDDER_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final CurrencyConversionService currencyConversionService;

    private static final TypeReference<ExtPrebid<?, ExtImpBidstack>> BIDDER_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    public BidstackBidder(String endpointUrl,
                          CurrencyConversionService currencyConversionService,
                          JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> updatedImps = new ArrayList<>();

        final MultiMap headers;
        try {
            headers = constructHeaders(request);
            for (Imp imp : request.getImp()) {
                updatedImps.add(updateImp(imp, request));
            }
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
        final BidRequest updatedBidRequest = updateBidRequest(request, updatedImps);

        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(headers)
                .body(mapper.encodeToBytes(updatedBidRequest))
                .payload(updatedBidRequest)
                .build());
    }

    private ExtImpBidstack parseExtImp(Imp imp) throws PreBidException {
        try {
            return mapper.mapper()
                    .convertValue(imp.getExt(), BIDDER_EXT_TYPE_REFERENCE)
                    .getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Unable to decode the impression ext for id: " + imp.getId());
        }
    }

    private Imp updateImp(Imp imp, BidRequest request) {
        final UpdateResult<BigDecimal> resolvedBidFloor = resolveBidFloor(request, imp);

        return resolvedBidFloor.isUpdated()
                ? imp.toBuilder()
                .bidfloorcur(BIDDER_CURRENCY)
                .bidfloor(resolvedBidFloor.getValue())
                .build()
                : imp;
    }

    private UpdateResult<BigDecimal> resolveBidFloor(BidRequest request, Imp imp) {
        return shouldConvertBidFloor(imp.getBidfloor(), imp.getBidfloorcur())
                ? UpdateResult.updated(convertBidFloorCurrency(
                imp.getBidfloor(), request, imp.getId(), imp.getBidfloorcur()))
                : UpdateResult.unaltered(imp.getBidfloor());
    }

    private static BidRequest updateBidRequest(BidRequest request, List<Imp> updatedImps) {
        return request.toBuilder()
                .imp(updatedImps)
                .build();
    }

    private static boolean shouldConvertBidFloor(BigDecimal bidFloor, String bidFloorCur) {
        return BidderUtil.isValidPrice(bidFloor) && !StringUtils.equalsIgnoreCase(bidFloorCur, BIDDER_CURRENCY);
    }

    private BigDecimal convertBidFloorCurrency(BigDecimal bidFloor,
                                               BidRequest bidRequest,
                                               String impId,
                                               String bidFloorCur) {
        try {
            return currencyConversionService
                    .convertCurrency(bidFloor, bidRequest, bidFloorCur, BIDDER_CURRENCY);
        } catch (PreBidException e) {
            throw new PreBidException("Unable to convert provided bid floor currency from %s to %s for imp `%s`"
                    .formatted(bidFloorCur, BIDDER_CURRENCY, impId));
        }
    }

    private MultiMap constructHeaders(BidRequest bidRequest) {
        final String publishedId = StringUtils.defaultString(parseExtImp(bidRequest.getImp().get(0)).getPublisherId());
        return HttpUtil.headers()
                .add(HttpUtil.AUTHORIZATION_HEADER.toString(), "Bearer " + publishedId);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException e) {
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
