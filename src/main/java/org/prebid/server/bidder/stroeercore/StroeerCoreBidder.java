package org.prebid.server.bidder.stroeercore;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Imp.ImpBuilder;
import com.iab.openrtb.response.Bid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.stroeercore.model.StroeercoreBid;
import org.prebid.server.bidder.stroeercore.model.StroeercoreBidResponse;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.stroeercore.ExtImpStroeerCore;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StroeerCoreBidder implements Bidder<BidRequest> {

    private static final String BIDDER_CURRENCY = "EUR";

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final CurrencyConversionService currencyConversionService;

    public StroeerCoreBidder(final String endpointUrl, final JacksonMapper mapper,
                             final CurrencyConversionService currencyConversionService) {
        this.endpointUrl = endpointUrl;
        this.mapper = mapper;
        this.currencyConversionService = currencyConversionService;
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(final BidRequest bidRequest) {
        final List<Imp> modifiedImps = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (final Imp imp: bidRequest.getImp()) {
            final ExtImpStroeerCore impExt;
            try {
                impExt = parseImpExt(imp);
                validate(imp, impExt);

                final ImpBuilder impBuilder = imp.toBuilder();

                if (shouldConvertBidFloor(imp)) {
                    convertBidFloor(bidRequest, imp, impBuilder);
                }

                impBuilder.tagid(impExt.getSlotId());

                modifiedImps.add(impBuilder.build());
            } catch (Exception e) {
                final String message = String.format("%s. Ignore imp id = %s.", e.getMessage(), imp.getId());
                errors.add(BidderError.badInput(message));
            }
        }

        if (modifiedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest outgoingRequest = bidRequest.toBuilder().imp(modifiedImps).build();

        return Result.of(Collections.singletonList(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .body(mapper.encodeToBytes(outgoingRequest))
                .payload(outgoingRequest)
                .headers(HttpUtil.headers())
                .build()), errors);
    }

    private void validate(final Imp imp, final ExtImpStroeerCore impExt) {
        if (StringUtils.isBlank(impExt.getSlotId())) {
            throw new PreBidException("Custom param slot id (sid) is empty");
        }
        if (imp.getBanner() == null) {
            throw new PreBidException("Expected banner impression");
        }
    }

    private void convertBidFloor(final BidRequest bidRequest, final Imp imp, final ImpBuilder impBuilder) {
        final BigDecimal convertedBidFloor = currencyConversionService
                .convertCurrency(imp.getBidfloor(), bidRequest, imp.getBidfloorcur(), BIDDER_CURRENCY);
        impBuilder.bidfloorcur(BIDDER_CURRENCY);
        impBuilder.bidfloor(convertedBidFloor);
    }

    private static boolean shouldConvertBidFloor(final Imp imp) {
        return BidderUtil.isValidPrice(imp.getBidfloor())
                && !StringUtils.equalsIgnoreCase(imp.getBidfloorcur(), BIDDER_CURRENCY);
    }

    @Override
    public Result<List<BidderBid>> makeBids(final HttpCall<BidRequest> httpCall, final BidRequest bidRequest) {
        final String body = httpCall.getResponse().getBody();

        final StroeercoreBidResponse response;
        try {
            response = mapper.decodeValue(body, StroeercoreBidResponse.class);
        } catch (Exception e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderBid> bidderBids = new ArrayList<>();

        for (final StroeercoreBid stroeerBid : response.getBids()) {
            final Bid bid = Bid.builder()
                    .id(stroeerBid.getId())
                    .impid(stroeerBid.getBidId())
                    .w(stroeerBid.getWidth())
                    .h(stroeerBid.getHeight())
                    .price(stroeerBid.getCpm())
                    .adm(stroeerBid.getAdMarkup())
                    .crid(stroeerBid.getCreativeId())
                    .build();

            bidderBids.add(BidderBid.of(bid, BidType.banner, BIDDER_CURRENCY));
        }

        return Result.withValues(bidderBids);
    }

    private ExtImpStroeerCore parseImpExt(final Imp imp) {
        final JsonNode extImpNode = imp.getExt().get("bidder");
        return mapper.mapper().convertValue(extImpNode, ExtImpStroeerCore.class);
    }
}
