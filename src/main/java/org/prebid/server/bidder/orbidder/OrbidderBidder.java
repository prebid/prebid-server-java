package org.prebid.server.bidder.orbidder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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

public class OrbidderBidder implements Bidder<BidRequest> {

    private static final String BIDDER_CURRENCY = "EUR";
    private static final TypeReference<ExtPrebid<?, ExtImpOrbidder>> ORBIDDER_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

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
                final BigDecimal bidFloor = resolveBidFloor(request, imp.getBidfloorcur(), imp.getBidfloor());
                parseImpExt(imp);
                validImps.add(modifyImp(imp, bidFloor));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        final BidRequest outgoingRequest = request.toBuilder().imp(validImps).build();

        return Result.of(Collections.singletonList(BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper)),
                errors);
    }

    private BigDecimal resolveBidFloor(BidRequest bidRequest, String bidfloorcur, BigDecimal bidfloor) {
        if (BidderUtil.isValidPrice(bidfloor)
                && !StringUtils.equalsIgnoreCase(bidfloorcur, BIDDER_CURRENCY)
                && StringUtils.isNotBlank(bidfloorcur)) {
            return currencyConversionService.convertCurrency(bidfloor, bidRequest, bidfloorcur, BIDDER_CURRENCY);
        }

        return bidfloor;
    }

    private static Imp modifyImp(Imp imp, BigDecimal bidFloor) {
        return imp.toBuilder()
                .bidfloorcur(BIDDER_CURRENCY)
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
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = decodeBodyToBidResponse(httpCall);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> toBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
        return Result.of(bidderBids, errors);
    }

    private BidResponse decodeBodyToBidResponse(BidderCall<BidRequest> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private BidderBid toBidderBid(Bid bid, String cur, List<BidderError> errors) {
        final BidType bidType;
        try {
            bidType = getBidType(bid.getMtype());
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }

        return BidderBid.of(bid, bidType, cur);
    }

    private static BidType getBidType(Integer mType) {
        return switch (mType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;

            default -> throw new PreBidException("Unsupported mType " + mType);
        };
    }
}
