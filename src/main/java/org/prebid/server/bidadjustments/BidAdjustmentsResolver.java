package org.prebid.server.bidadjustments;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.bidadjustments.model.BidAdjustmentType;
import org.prebid.server.bidadjustments.model.BidAdjustmentsRule;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.util.BidderUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public class BidAdjustmentsResolver {

    private final CurrencyConversionService currencyService;
    private final BidAdjustmentsRulesResolver bidAdjustmentsRulesResolver;

    public BidAdjustmentsResolver(CurrencyConversionService currencyService,
                                  BidAdjustmentsRulesResolver bidAdjustmentsRulesResolver) {

        this.currencyService = Objects.requireNonNull(currencyService);
        this.bidAdjustmentsRulesResolver = Objects.requireNonNull(bidAdjustmentsRulesResolver);
    }

    public Price resolve(Price initialPrice,
                         BidRequest bidRequest,
                         ImpMediaType targetMediaType,
                         String targetBidder,
                         String targetDealId) {

        final List<BidAdjustmentsRule> rules = bidAdjustmentsRulesResolver.resolve(
                bidRequest, targetMediaType, targetBidder, targetDealId);

        return adjustPrice(initialPrice, rules, bidRequest);
    }

    private Price adjustPrice(Price price,
                              List<BidAdjustmentsRule> bidAdjustmentRules,
                              BidRequest bidRequest) {

        String resolvedCurrency = price.getCurrency();
        BigDecimal resolvedPrice = price.getValue();

        for (BidAdjustmentsRule rule : bidAdjustmentRules) {
            final BidAdjustmentType adjustmentType = rule.getAdjType();
            final BigDecimal adjustmentValue = rule.getValue();
            final String adjustmentCurrency = rule.getCurrency();

            switch (adjustmentType) {
                case MULTIPLIER -> resolvedPrice = BidderUtil.roundFloor(resolvedPrice.multiply(adjustmentValue));
                case CPM -> {
                    final BigDecimal convertedAdjustmentValue = currencyService.convertCurrency(
                            adjustmentValue, bidRequest, adjustmentCurrency, resolvedCurrency);
                    resolvedPrice = BidderUtil.roundFloor(resolvedPrice.subtract(convertedAdjustmentValue));
                }
                case STATIC -> {
                    resolvedPrice = adjustmentValue;
                    resolvedCurrency = adjustmentCurrency;
                }
            }
        }

        return Price.of(resolvedCurrency, resolvedPrice);
    }
}
