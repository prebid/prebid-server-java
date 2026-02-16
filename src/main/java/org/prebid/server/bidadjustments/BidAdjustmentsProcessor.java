package org.prebid.server.bidadjustments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.ImpMediaTypeResolver;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;
import org.prebid.server.util.PbsUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class BidAdjustmentsProcessor {

    private static final String ORIGINAL_BID_CPM = "origbidcpm";
    private static final String ORIGINAL_BID_CURRENCY = "origbidcur";
    private static final String PREBID_EXT = "prebid";

    private final CurrencyConversionService currencyService;
    private final BidAdjustmentFactorResolver bidAdjustmentFactorResolver;
    private final BidAdjustmentsResolver bidAdjustmentsResolver;
    private final ObjectMapper mapper;

    public BidAdjustmentsProcessor(CurrencyConversionService currencyService,
                                   BidAdjustmentFactorResolver bidAdjustmentFactorResolver,
                                   BidAdjustmentsResolver bidAdjustmentsResolver,
                                   JacksonMapper mapper) {

        this.currencyService = Objects.requireNonNull(currencyService);
        this.bidAdjustmentFactorResolver = Objects.requireNonNull(bidAdjustmentFactorResolver);
        this.bidAdjustmentsResolver = Objects.requireNonNull(bidAdjustmentsResolver);
        this.mapper = Objects.requireNonNull(mapper).mapper();
    }

    public AuctionParticipation enrichWithAdjustedBids(AuctionParticipation auctionParticipation,
                                                       BidRequest bidRequest) {

        if (auctionParticipation.isRequestBlocked()) {
            return auctionParticipation;
        }

        final BidderResponse bidderResponse = auctionParticipation.getBidderResponse();
        final BidderSeatBid seatBid = bidderResponse.getSeatBid();

        final List<BidderBid> bidderBids = seatBid.getBids();
        if (bidderBids.isEmpty()) {
            return auctionParticipation;
        }

        final List<BidderError> errors = new ArrayList<>(seatBid.getErrors());
        final String bidder = auctionParticipation.getBidder();

        final List<BidderBid> updatedBidderBids = bidderBids.stream()
                .map(bidderBid -> applyBidAdjustments(bidderBid, bidRequest, bidder, errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        final BidderResponse updatedBidderResponse = bidderResponse.with(seatBid.toBuilder()
                .bids(updatedBidderBids)
                .errors(errors)
                .build());

        return auctionParticipation.with(updatedBidderResponse);
    }

    private BidderBid applyBidAdjustments(BidderBid bidderBid,
                                          BidRequest bidRequest,
                                          String bidder,
                                          List<BidderError> errors) {
        try {
            final Price originalPrice = getOriginalPrice(bidderBid);

            final ImpMediaType mediaType = ImpMediaTypeResolver.resolve(
                    bidderBid.getBid().getImpid(),
                    bidRequest.getImp(),
                    bidderBid.getType());

            final Price priceWithFactorsApplied = applyBidAdjustmentFactors(
                    originalPrice,
                    getAdapterCode(bidderBid.getBid()),
                    bidderBid.getSeat(),
                    bidRequest,
                    mediaType);

            final Price priceWithAdjustmentsApplied = applyBidAdjustmentRules(
                    priceWithFactorsApplied,
                    bidderBid.getSeat(),
                    bidder,
                    bidRequest,
                    mediaType,
                    bidderBid.getBid().getDealid());

            return updateBid(originalPrice, priceWithAdjustmentsApplied, bidderBid, bidRequest);
        } catch (PreBidException e) {
            errors.add(BidderError.generic(e.getMessage()));
            return null;
        }
    }

    private String getAdapterCode(Bid bid) {
        return Optional.ofNullable(bid.getExt())
                .filter(ext -> ext.hasNonNull(PREBID_EXT))
                .map(this::convertValue)
                .map(ExtBidPrebid::getMeta)
                .map(ExtBidPrebidMeta::getAdapterCode)
                .orElse(null);
    }

    private ExtBidPrebid convertValue(JsonNode jsonNode) {
        try {
            return mapper.convertValue(jsonNode.get(PREBID_EXT), ExtBidPrebid.class);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private BidderBid updateBid(Price originalPrice, Price adjustedPrice, BidderBid bidderBid, BidRequest bidRequest) {
        final Bid bid = bidderBid.getBid();
        final ObjectNode bidExt = bid.getExt();
        final ObjectNode updatedBidExt = bidExt != null ? bidExt : mapper.createObjectNode();

        final BigDecimal originalBidPrice = originalPrice.getValue();
        final String originalBidCurrency = originalPrice.getCurrency();
        updatedBidExt.set(ORIGINAL_BID_CPM, new DecimalNode(originalBidPrice));
        if (StringUtils.isNotBlank(originalBidCurrency)) {
            updatedBidExt.set(ORIGINAL_BID_CURRENCY, new TextNode(originalBidCurrency));
        }

        final String requestCurrency = bidRequest.getCur().getFirst();
        final BigDecimal requestCurrencyPrice = currencyService.convertCurrency(
                adjustedPrice.getValue(),
                bidRequest,
                adjustedPrice.getCurrency(),
                requestCurrency);

        return bidderBid.toBuilder()
                .bidCurrency(requestCurrency)
                .bid(bid.toBuilder()
                        .ext(updatedBidExt)
                        .price(requestCurrencyPrice)
                        .build())
                .build();
    }

    private Price getOriginalPrice(BidderBid bidderBid) {
        final Bid bid = bidderBid.getBid();
        final String bidCurrency = bidderBid.getBidCurrency();
        final BigDecimal price = bid.getPrice();

        return Price.of(StringUtils.stripToNull(bidCurrency), price);
    }

    private Price applyBidAdjustmentFactors(Price bidPrice,
                                            String bidder,
                                            String seat,
                                            BidRequest bidRequest,
                                            ImpMediaType mediaType) {

        final String bidCurrency = bidPrice.getCurrency();
        final BigDecimal price = bidPrice.getValue();

        final BigDecimal priceAdjustmentFactor = bidAdjustmentForBidder(bidder, seat, bidRequest, mediaType);
        final BigDecimal adjustedPrice = adjustPrice(priceAdjustmentFactor, price);

        return Price.of(bidCurrency, adjustedPrice.compareTo(price) != 0 ? adjustedPrice : price);
    }

    private BigDecimal bidAdjustmentForBidder(String bidder,
                                              String seat,
                                              BidRequest bidRequest,
                                              ImpMediaType mediaType) {

        final ExtRequestBidAdjustmentFactors adjustmentFactors = extBidAdjustmentFactors(bidRequest);
        return adjustmentFactors == null
                ? null
                : bidAdjustmentFactorResolver.resolve(mediaType, adjustmentFactors, bidder, seat);
    }

    private static ExtRequestBidAdjustmentFactors extBidAdjustmentFactors(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = PbsUtil.extRequestPrebid(bidRequest);
        return prebid != null ? prebid.getBidadjustmentfactors() : null;
    }

    private static BigDecimal adjustPrice(BigDecimal priceAdjustmentFactor, BigDecimal price) {
        return priceAdjustmentFactor != null && priceAdjustmentFactor.compareTo(BigDecimal.ONE) != 0
                ? price.multiply(priceAdjustmentFactor)
                : price;
    }

    private Price applyBidAdjustmentRules(Price bidPrice,
                                          String seat,
                                          String bidder,
                                          BidRequest bidRequest,
                                          ImpMediaType mediaType,
                                          String dealId) {

        return bidAdjustmentsResolver.resolve(
                bidPrice,
                bidRequest,
                mediaType,
                seat,
                bidder,
                dealId);
    }
}
