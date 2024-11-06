package org.prebid.server.bidadjustments;

import com.iab.openrtb.request.BidRequest;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidadjustments.model.BidAdjustmentType;
import org.prebid.server.bidadjustments.model.BidAdjustments;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentsRule;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.dsl.config.PrebidConfigMatchingStrategy;
import org.prebid.server.util.dsl.config.PrebidConfigParameter;
import org.prebid.server.util.dsl.config.PrebidConfigParameters;
import org.prebid.server.util.dsl.config.PrebidConfigSource;
import org.prebid.server.util.dsl.config.impl.MostAccurateCombinationStrategy;
import org.prebid.server.util.dsl.config.impl.SimpleDirectParameter;
import org.prebid.server.util.dsl.config.impl.SimpleParameters;
import org.prebid.server.util.dsl.config.impl.SimpleSource;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BidAdjustmentsResolver {

    public static final String WILDCARD = "*";
    public static final String DELIMITER = "|";

    private final PrebidConfigMatchingStrategy matchingStrategy;
    private final CurrencyConversionService currencyService;

    public BidAdjustmentsResolver(CurrencyConversionService currencyService) {
        this.currencyService = Objects.requireNonNull(currencyService);
        this.matchingStrategy = new MostAccurateCombinationStrategy();
    }

    public Price resolve(Price initialPrice,
                         BidRequest bidRequest,
                         BidAdjustments bidAdjustments,
                         ImpMediaType targetMediaType,
                         String targetBidder,
                         String targetDealId) {

        final List<ExtRequestBidAdjustmentsRule> adjustmentsRules = findRules(
                bidAdjustments,
                targetMediaType,
                targetBidder,
                targetDealId);

        return adjustPrice(initialPrice, adjustmentsRules, bidRequest);
    }

    private List<ExtRequestBidAdjustmentsRule> findRules(BidAdjustments bidAdjustments,
                                                         ImpMediaType targetMediaType,
                                                         String targetBidder,
                                                         String targetDealId) {

        final Map<String, List<ExtRequestBidAdjustmentsRule>> rules = bidAdjustments.getRules();
        final PrebidConfigSource source = SimpleSource.of(WILDCARD, DELIMITER, rules.keySet());
        final PrebidConfigParameters parameters = createParameters(targetMediaType, targetBidder, targetDealId);

        final String rule = matchingStrategy.match(source, parameters);
        return rule == null ? Collections.emptyList() : rules.get(rule);
    }

    private PrebidConfigParameters createParameters(ImpMediaType mediaType, String bidder, String dealId) {
        final List<PrebidConfigParameter> conditionsMatchers = List.of(
                SimpleDirectParameter.of(mediaType.toString()),
                SimpleDirectParameter.of(bidder),
                StringUtils.isNotBlank(dealId) ? SimpleDirectParameter.of(dealId) : PrebidConfigParameter.wildcard());

        return SimpleParameters.of(conditionsMatchers);
    }

    private Price adjustPrice(Price price,
                              List<ExtRequestBidAdjustmentsRule> bidAdjustmentRules,
                              BidRequest bidRequest) {

        String resolvedCurrency = price.getCurrency();
        BigDecimal resolvedPrice = price.getValue();

        for (ExtRequestBidAdjustmentsRule rule : bidAdjustmentRules) {
            final BidAdjustmentType adjustmentType = rule.getAdjType();
            final BigDecimal adjustmentValue = rule.getValue();
            final String adjustmentCurrency = rule.getCurrency();

            if (adjustmentType == BidAdjustmentType.MULTIPLIER) {
                resolvedPrice = BidderUtil.roundFloor(resolvedPrice.multiply(adjustmentValue));
            } else if (adjustmentType == BidAdjustmentType.CPM) {
                final BigDecimal convertedAdjustmentValue = currencyService.convertCurrency(
                        adjustmentValue, bidRequest, adjustmentCurrency, resolvedCurrency);
                resolvedPrice = BidderUtil.roundFloor(resolvedPrice.subtract(convertedAdjustmentValue));
            } else if (adjustmentType == BidAdjustmentType.STATIC) {
                resolvedPrice = adjustmentValue;
                resolvedCurrency = adjustmentCurrency;
            }
        }

        return Price.of(resolvedCurrency, resolvedPrice);
    }
}
