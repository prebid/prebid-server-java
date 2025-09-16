package org.prebid.server.bidadjustments;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidadjustments.model.BidAdjustmentType;
import org.prebid.server.bidadjustments.model.BidAdjustments;
import org.prebid.server.bidadjustments.model.BidAdjustmentsRule;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.validation.ValidationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BidAdjustmentRulesValidator {

    public static final Set<String> SUPPORTED_MEDIA_TYPES = Set.of(
            BidAdjustmentsRulesResolver.WILDCARD,
            ImpMediaType.banner.toString(),
            ImpMediaType.audio.toString(),
            ImpMediaType.video_instream.toString(),
            ImpMediaType.video_outstream.toString(),
            ImpMediaType.xNative.toString());

    private BidAdjustmentRulesValidator() {

    }

    public static void validate(BidAdjustments bidAdjustments) throws ValidationException {
        if (bidAdjustments == null) {
            return;
        }

        final Map<String, Map<String, Map<String, List<BidAdjustmentsRule>>>> mediatypes =
                bidAdjustments.getRules();

        if (MapUtils.isEmpty(mediatypes)) {
            return;
        }

        for (String mediatype : mediatypes.keySet()) {
            if (SUPPORTED_MEDIA_TYPES.contains(mediatype)) {
                final Map<String, Map<String, List<BidAdjustmentsRule>>> bidders = mediatypes.get(mediatype);
                if (MapUtils.isEmpty(bidders)) {
                    throw new ValidationException("no bidders found in %s".formatted(mediatype));
                }
                for (String bidder : bidders.keySet()) {
                    final Map<String, List<BidAdjustmentsRule>> deals = bidders.get(bidder);

                    if (MapUtils.isEmpty(deals)) {
                        throw new ValidationException("no deals found in %s.%s".formatted(mediatype, bidder));
                    }

                    for (String dealId : deals.keySet()) {
                        final String path = "%s.%s.%s".formatted(mediatype, bidder, dealId);
                        validateRules(deals.get(dealId), path);
                    }
                }
            }
        }
    }

    private static void validateRules(List<BidAdjustmentsRule> rules,
                                      String path) throws ValidationException {

        if (rules == null) {
            throw new ValidationException("no bid adjustment rules found in %s".formatted(path));
        }

        for (BidAdjustmentsRule rule : rules) {
            final BidAdjustmentType type = rule.getAdjType();
            final String currency = rule.getCurrency();
            final BigDecimal value = rule.getValue();

            final boolean isNotSpecifiedCurrency = StringUtils.isBlank(currency);

            final boolean unknownType = type == null || type == BidAdjustmentType.UNKNOWN;

            final boolean invalidCpm = type == BidAdjustmentType.CPM
                    && (isNotSpecifiedCurrency || isValueNotInRange(value, 0, Integer.MAX_VALUE));

            final boolean invalidMultiplier = type == BidAdjustmentType.MULTIPLIER
                    && isValueNotInRange(value, 0, 100);

            final boolean invalidStatic = type == BidAdjustmentType.STATIC
                    && (isNotSpecifiedCurrency || isValueNotInRange(value, 0, Integer.MAX_VALUE));

            if (unknownType || invalidCpm || invalidMultiplier || invalidStatic) {
                throw new ValidationException("the found rule %s in %s is invalid".formatted(rule, path));
            }
        }
    }

    private static boolean isValueNotInRange(BigDecimal value, int minValue, int maxValue) {
        return value == null
                || value.compareTo(BigDecimal.valueOf(minValue)) < 0
                || value.compareTo(BigDecimal.valueOf(maxValue)) >= 0;
    }
}
