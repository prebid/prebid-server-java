package org.prebid.server.bidder.sonobi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.sonobi.ExtImpSonobi;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SonobiBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSonobi>> SONOBI_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String BIDDER_CURRENCY = "USD";

    private final CurrencyConversionService currencyConversionService;
    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SonobiBidder(CurrencyConversionService currencyConversionService,
                        String endpointUrl,
                        JacksonMapper mapper) {

        this.currencyConversionService = currencyConversionService;
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpSonobi extImpSonobi = parseImpExt(imp);
                final Imp modifiedImp = modifyImp(bidRequest, imp, extImpSonobi.getTagId());
                requests.add(makeRequest(bidRequest, modifiedImp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private ExtImpSonobi parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), SONOBI_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp modifyImp(BidRequest bidRequest, Imp imp, String tagId) {
        final Price bidFloor = resolveBidFloor(bidRequest, imp);
        return imp.toBuilder()
                .tagid(tagId)
                .bidfloor(bidFloor.getValue())
                .bidfloorcur(bidFloor.getCurrency())
                .build();
    }

    private Price resolveBidFloor(BidRequest bidRequest, Imp imp) {
        final BigDecimal bidFloor = imp.getBidfloor();
        final String bidFloorCurrency = imp.getBidfloorcur();

        if (BidderUtil.isValidPrice(bidFloor)
                && StringUtils.isNotBlank(bidFloorCurrency)
                && !StringUtils.equalsIgnoreCase(bidFloorCurrency, BIDDER_CURRENCY)) {
            return Price.of(
                    BIDDER_CURRENCY,
                    currencyConversionService.convertCurrency(bidFloor, bidRequest, bidFloorCurrency, BIDDER_CURRENCY));
        }

        return Price.of(bidFloorCurrency, bidFloor);
    }

    private HttpRequest<BidRequest> makeRequest(BidRequest bidRequest, Imp imp) {
        final BidRequest modifiedBidRequest = bidRequest.toBuilder()
                .cur(Collections.singletonList(BIDDER_CURRENCY))
                .imp(Collections.singletonList(imp))
                .build();

        return BidderUtil.defaultRequest(modifiedBidRequest, endpointUrl, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest,
                                               BidResponse bidResponse) {

        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, resolveBidType(bid.getImpid(), bidRequest.getImp()), BIDDER_CURRENCY))
                .toList();
    }

    private static BidType resolveBidType(String impId, List<Imp> imps) throws PreBidException {
        for (Imp imp : imps) {
            if (Objects.equals(impId, imp.getId())) {
                if (imp.getBanner() == null && imp.getVideo() != null) {
                    return BidType.video;
                }
                if (imp.getBanner() == null && imp.getVideo() == null && imp.getXNative() != null) {
                    return BidType.xNative;
                }
                return BidType.banner;
            }
        }

        throw new PreBidException("Failed to find impression for ID: " + impId);
    }
}
