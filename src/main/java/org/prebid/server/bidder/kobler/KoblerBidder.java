package org.prebid.server.bidder.kobler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.kobler.ExtImpKobler;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class KoblerBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpKobler>> KOBLER_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String EXT_PREBID = "prebid";
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final String DEV_ENDPOINT = "https://bid-service.dev.essrtb.com/bid/prebid_server_rtb_call";

    private final String endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    public KoblerBidder(String endpointUrl,
                        CurrencyConversionService currencyConversionService,
                        JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(endpointUrl);
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        boolean testMode = false;
        final List<Imp> modifiedImps = new ArrayList<>();

        final List<String> currencies = bidRequest.getCur() != null
                ? new ArrayList<>(bidRequest.getCur())
                : new ArrayList<>();
        if (!currencies.contains(DEFAULT_BID_CURRENCY)) {
            currencies.add(DEFAULT_BID_CURRENCY);
        }

        BidRequest modifiedRequest = bidRequest.toBuilder().cur(currencies).build();

        for (Imp imp : modifiedRequest.getImp()) {
            try {
                final Imp processedImp = processImp(modifiedRequest, imp, errors);
                modifiedImps.add(processedImp);

                if (modifiedImps.size() == 1) {
                    testMode = extractTestMode(processedImp);
                }
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (modifiedImps.isEmpty()) {
            errors.add(BidderError.badInput("No valid impressions"));
            return Result.withErrors(errors);
        }

        modifiedRequest = modifiedRequest.toBuilder().imp(modifiedImps).build();

        final String endpoint = testMode ? DEV_ENDPOINT : endpointUrl;

        try {
            return Result.of(Collections.singletonList(
                    HttpRequest.<BidRequest>builder()
                            .method(HttpMethod.POST)
                            .uri(endpoint)
                            .headers(HttpUtil.headers())
                            .body(mapper.encodeToBytes(modifiedRequest))
                            .payload(modifiedRequest)
                            .build()
            ), errors);
        } catch (EncodeException e) {
            errors.add(BidderError.badInput("Failed to encode request: " + e.getMessage()));
            return Result.withErrors(errors);
        }
    }

    private Imp processImp(BidRequest bidRequest, Imp imp, List<BidderError> errors) {
        if (imp.getBidfloor() != null
                && imp.getBidfloor().compareTo(BigDecimal.ZERO) > 0
                && imp.getBidfloorcur() != null) {
            final String bidFloorCur = imp.getBidfloorcur().toUpperCase();
            if (!DEFAULT_BID_CURRENCY.equals(bidFloorCur)) {
                try {
                    final BigDecimal convertedPrice = currencyConversionService.convertCurrency(
                            imp.getBidfloor(),
                            bidRequest,
                            bidFloorCur,
                            DEFAULT_BID_CURRENCY
                    );
                    return imp.toBuilder()
                            .bidfloor(convertedPrice)
                            .bidfloorcur(DEFAULT_BID_CURRENCY)
                            .build();
                } catch (PreBidException e) {
                    errors.add(BidderError.badInput(e.getMessage()));
                }
            }
        }
        return imp;
    }

    public boolean extractTestMode(Imp imp) {
        try {
            final ExtPrebid<?, ExtImpKobler> extPrebid = mapper.mapper().convertValue(imp.getExt(),
                    KOBLER_EXT_TYPE_REFERENCE);
            final ExtImpKobler extImpKobler = extPrebid != null ? extPrebid.getBidder() : null;
            return extImpKobler != null && Boolean.TRUE.equals(extImpKobler.getTest());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, errors);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid), bidResponse.getCur()))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidType getBidType(Bid bid) {
        if (bid.getExt() == null) {
            return BidType.banner;
        }

        final ObjectNode prebidNode = (ObjectNode) bid.getExt().get(EXT_PREBID);
        if (prebidNode == null) {
            return BidType.banner;
        }

        final ExtBidPrebid extBidPrebid = parseExtBidPrebid(prebidNode);
        if (extBidPrebid == null || extBidPrebid.getType() == null) {
            return BidType.banner;
        }

        return extBidPrebid.getType(); // jeśli udało się sparsować
    }

    private ExtBidPrebid parseExtBidPrebid(ObjectNode prebid) {
        try {
            return mapper.mapper().treeToValue(prebid, ExtBidPrebid.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
