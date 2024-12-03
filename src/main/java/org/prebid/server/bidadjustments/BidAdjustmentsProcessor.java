package org.prebid.server.bidadjustments;

import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.ImpMediaTypeResolver;
import org.prebid.server.auction.adjustment.BidAdjustmentFactorResolver;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidadjustments.model.BidAdjustments;
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
import org.prebid.server.util.PbsUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class BidAdjustmentsProcessor {

    private static final String ORIGINAL_BID_CPM = "origbidcpm";
    private static final String ORIGINAL_BID_CURRENCY = "origbidcur";

    private final CurrencyConversionService currencyService;
    private final BidAdjustmentFactorResolver bidAdjustmentFactorResolver;
    private final BidAdjustmentsResolver bidAdjustmentsResolver;
    private final JacksonMapper mapper;

    public BidAdjustmentsProcessor(CurrencyConversionService currencyService,
                                   BidAdjustmentFactorResolver bidAdjustmentFactorResolver,
                                   BidAdjustmentsResolver bidAdjustmentsResolver,
                                   JacksonMapper mapper) {

        this.currencyService = Objects.requireNonNull(currencyService);
        this.bidAdjustmentFactorResolver = Objects.requireNonNull(bidAdjustmentFactorResolver);
        this.bidAdjustmentsResolver = Objects.requireNonNull(bidAdjustmentsResolver);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public AuctionParticipation enrichWithAdjustedBids(AuctionParticipation auctionParticipation,
                                                       BidRequest bidRequest,
                                                       BidAdjustments bidAdjustments) {

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
                .map(bidderBid -> applyBidAdjustments(bidderBid, bidRequest, bidder, bidAdjustments, errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        final BidderResponse updatedBidderResponse = bidderResponse.with(seatBid.toBuilder()
                .bids(updatedBidderBids)
                .errors(errors)
                .build());

        return auctionParticipation.with(updatedBidderResponse);
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

    private BidderBid applyBidAdjustments(BidderBid bidderBid,
                                          BidRequest bidRequest,
                                          String bidder,
                                          BidAdjustments bidAdjustments,
                                          List<BidderError> errors) {
        try {
            final Price originalPrice = getOriginalPrice(bidderBid);
            final Price priceWithFactorsApplied = applyBidAdjustmentFactors(
                    originalPrice,
                    bidderBid,
                    bidder,
                    bidRequest);
            final Price priceWithAdjustmentsApplied = applyBidAdjustmentRules(
                    priceWithFactorsApplied,
                    bidderBid,
                    bidder,
                    bidRequest,
                    bidAdjustments);
            return updateBid(originalPrice, priceWithAdjustmentsApplied, bidderBid, bidRequest);
        } catch (PreBidException e) {
            errors.add(BidderError.generic(e.getMessage()));
            return null;
        }
    }

    private BidderBid updateBid(Price originalPrice, Price adjustedPrice, BidderBid bidderBid, BidRequest bidRequest) {
        final Bid bid = bidderBid.getBid();
        final ObjectNode bidExt = bid.getExt();
        final ObjectNode updatedBidExt = bidExt != null ? bidExt : mapper.mapper().createObjectNode();

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

    private Price applyBidAdjustmentFactors(Price bidPrice, BidderBid bidderBid, String bidder, BidRequest bidRequest) {
        final String bidCurrency = bidPrice.getCurrency();
        final BigDecimal price = bidPrice.getValue();

        final BigDecimal priceAdjustmentFactor = bidAdjustmentForBidder(bidder, bidRequest, bidderBid);
        final BigDecimal adjustedPrice = adjustPrice(priceAdjustmentFactor, price);

        return Price.of(bidCurrency, adjustedPrice.compareTo(price) != 0 ? adjustedPrice : price);
    }

    private BigDecimal bidAdjustmentForBidder(String bidder, BidRequest bidRequest, BidderBid bidderBid) {
        final ExtRequestBidAdjustmentFactors adjustmentFactors = extBidAdjustmentFactors(bidRequest);
        if (adjustmentFactors == null) {
            return null;
        }

        final ImpMediaType mediaType = ImpMediaTypeResolver.resolve(
                bidderBid.getBid().getImpid(),
                bidRequest.getImp(),
                bidderBid.getType());

        return bidAdjustmentFactorResolver.resolve(mediaType, adjustmentFactors, bidder);
    }

    private Price applyBidAdjustmentRules(Price bidPrice,
                                          BidderBid bidderBid,
                                          String bidder,
                                          BidRequest bidRequest,
                                          BidAdjustments bidAdjustments) {

        final Bid bid = bidderBid.getBid();
        final String bidCurrency = bidPrice.getCurrency();
        final BigDecimal price = bidPrice.getValue();

        final ImpMediaType mediaType = ImpMediaTypeResolver.resolve(
                bid.getImpid(),
                bidRequest.getImp(),
                bidderBid.getType());

        return bidAdjustmentsResolver.resolve(
                Price.of(bidCurrency, price),
                bidRequest,
                bidAdjustments,
                mediaType == null || mediaType == ImpMediaType.video ? ImpMediaType.video_instream : mediaType,
                bidder,
                bid.getDealid());
    }
}
