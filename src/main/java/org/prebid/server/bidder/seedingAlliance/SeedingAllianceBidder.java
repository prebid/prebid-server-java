package org.prebid.server.bidder.seedingAlliance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.seedingalliance.ExtImpSeedingAlliance;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SeedingAllianceBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSeedingAlliance>> EXT_IMP_TYPE_REFERENCE =
            new TypeReference<>() { };

    private static final String EUR_CURRENCY = "EUR";
    private static final String AUCTION_PRICE_MACRO = "${AUCTION_PRICE}";
    private static final String ACCOUNT_ID_MACRO = "{{AccountId}}";
    private static final String DEFAULT_SEAT_ID = "pbs";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SeedingAllianceBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        String seatId = null;
        final List<Imp> modifiedImps = new ArrayList<>();
        for (Imp imp: bidRequest.getImp()) {
            try {
                final ExtImpSeedingAlliance impExt = parseImpExt(imp);
                seatId = impExt.getSeatId();
                final Imp modifiedImp = imp.toBuilder().tagid(impExt.getAdUnitId()).build();
                modifiedImps.add(modifiedImp);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
        }
        final BidRequest modifiedBidRequest = modifyBidRequest(bidRequest, modifiedImps);

        return Result.withValue(makeHttpRequest(seatId, modifiedBidRequest));
    }

    private ExtImpSeedingAlliance parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), EXT_IMP_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("could not unmarshal imp.ext.prebid.bidder: " + e.getMessage());
        }
    }

    private static BidRequest modifyBidRequest(BidRequest bidRequest, List<Imp> modifiedImps) {
        return bidRequest.toBuilder()
                .imp(modifiedImps)
                .cur(modifyCurrencies(bidRequest.getCur()))
                .build();
    }

    private static List<String> modifyCurrencies(List<String> bidderCurrencies) {
        if (bidderCurrencies != null && bidderCurrencies.contains(EUR_CURRENCY)) {
            return bidderCurrencies;
        }

        final List<String> resolvedCurrencies = bidderCurrencies != null
                ? new ArrayList<>(bidderCurrencies)
                : new ArrayList<>();
        resolvedCurrencies.add(EUR_CURRENCY);

        return Collections.unmodifiableList(resolvedCurrencies);
    }

    private HttpRequest<BidRequest> makeHttpRequest(String seatId, BidRequest modifiedBidRequest) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(makeEndpoint(seatId))
                .headers(HttpUtil.headers())
                .body(mapper.encodeToBytes(modifiedBidRequest))
                .impIds(BidderUtil.impIds(modifiedBidRequest))
                .payload(modifiedBidRequest)
                .build();
    }

    private String makeEndpoint(String seatId) {
        final String accountId = StringUtils.isNotBlank(seatId) ? seatId : DEFAULT_SEAT_ID;
        return endpointUrl.replace(ACCOUNT_ID_MACRO, accountId);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            final List<BidderBid> bidderBids = extractBids(bidResponse, errors);
            return Result.of(bidderBids, errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid makeBidderBid(Bid bid, String bidCurrency, List<BidderError> errors) {
        final JsonNode typeNode = Optional.ofNullable(bid.getExt())
                .map(extNode -> extNode.get("prebid"))
                .map(extPrebidNode -> extPrebidNode.get("type"))
                .orElse(null);
        final BidType bidType;
        try {
            bidType = mapper.mapper().convertValue(typeNode, BidType.class);
        } catch (IllegalArgumentException e) {
            addMediaTypeParseError(errors, bid.getId());
            return null;
        }

        if (bidType == null) {
            addMediaTypeParseError(errors, bid.getId());
            return null;
        }

        return BidderBid.of(resolveBid(bid), bidType, bidCurrency);
    }

    private static void addMediaTypeParseError(List<BidderError> errors, String bidId) {
        errors.add(BidderError.badServerResponse(
                "Failed to parse bid.ext.prebid.type for bid.id: '%s'"
                        .formatted(bidId)));
    }

    private static Bid resolveBid(Bid bid) {
        final BigDecimal bidPrice = bid.getPrice();
        final String bidAdm = bid.getAdm();
        if (bidPrice == null || bidAdm == null) {
            return bid;
        }
        final String resolvedAdm = bidAdm
                .replace(AUCTION_PRICE_MACRO, bidPrice.stripTrailingZeros().toPlainString());

        return bid.toBuilder()
                .adm(resolvedAdm)
                .build();
    }
}
