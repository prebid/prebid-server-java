package org.prebid.server.bidder.orbidder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.orbidder.ExtImpOrbidder;
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

public class OrbidderBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpOrbidder>> ORBIDDER_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    private static final String DEFAULT_CURRENCY = "EUR";

    public OrbidderBidder(String endpointUrl,
                          CurrencyConversionService currencyConversionService,
                          JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final BigDecimal bidFloor = parseBidFloorCurrency(request, imp.getBidfloorcur(), imp.getBidfloor());
                parseImpExt(imp);
                validImps.add(modifyImp(imp, bidFloor));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        final BidRequest outgoingRequest = request.toBuilder().imp(validImps).build();

        return Result.of(Collections.singletonList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(endpointUrl)
                                .headers(HttpUtil.headers())
                                .payload(outgoingRequest)
                                .body(mapper.encodeToBytes(outgoingRequest))
                                .build()),
                errors);
    }

    private BigDecimal parseBidFloorCurrency(BidRequest bidRequest, String bidfloorcur, BigDecimal bidfloor) {
        if (BidderUtil.isValidPrice(bidfloor)
                && !StringUtils.equalsIgnoreCase(bidfloorcur, DEFAULT_CURRENCY)
                && StringUtils.isNotBlank(bidfloorcur)) {
            return currencyConversionService.convertCurrency(bidfloor, bidRequest, bidfloorcur, DEFAULT_CURRENCY);
        }

        return bidfloor;
    }

    private Imp modifyImp(Imp imp, BigDecimal bidFloor) {
        return imp.toBuilder()
                .bidfloorcur(DEFAULT_CURRENCY)
                .bidfloor(bidFloor)
                .build();
    }

    private void parseImpExt(Imp imp) {
        try {
            mapper.mapper().convertValue(imp.getExt(), ORBIDDER_EXT_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = decodeBodyToBidResponse(httpCall);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderBid> bidderBids = bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.banner, bidResponse.getCur()))
                .collect(Collectors.toList());
        return Result.of(bidderBids, Collections.emptyList());
    }

    private BidResponse decodeBodyToBidResponse(HttpCall<BidRequest> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }
}
