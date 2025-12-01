package org.prebid.server.bidadjustments.model;

import lombok.Value;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.prebid.server.bidadjustments.BidAdjustmentRulesValidator;
import org.prebid.server.bidadjustments.BidAdjustmentsRulesResolver;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class BidAdjustmentsRules {

    private static final String RULE_SCHEME =
            "%s" + BidAdjustmentsRulesResolver.DELIMITER + "%s" + BidAdjustmentsRulesResolver.DELIMITER + "%s";

    Map<String, List<BidAdjustmentsRule>> rules;

    public static BidAdjustmentsRules of(BidAdjustments bidAdjustments) {
        if (bidAdjustments == null) {
            return BidAdjustmentsRules.of(Collections.emptyMap());
        }

        final Map<String, List<BidAdjustmentsRule>> rules = new CaseInsensitiveMap<>();

        final Map<String, Map<String, Map<String, List<BidAdjustmentsRule>>>> mediatypes =
                bidAdjustments.getRules();

        if (MapUtils.isEmpty(mediatypes)) {
            return BidAdjustmentsRules.of(Collections.emptyMap());
        }

        for (String mediatype : mediatypes.keySet()) {
            if (BidAdjustmentRulesValidator.SUPPORTED_MEDIA_TYPES.contains(mediatype)) {
                final Map<String, Map<String, List<BidAdjustmentsRule>>> bidders = mediatypes.get(mediatype);
                for (String bidder : bidders.keySet()) {
                    final Map<String, List<BidAdjustmentsRule>> deals = bidders.get(bidder);
                    for (String dealId : deals.keySet()) {
                        rules.put(RULE_SCHEME.formatted(mediatype, bidder, dealId), deals.get(dealId));
                    }
                }
            }
        }

        return BidAdjustmentsRules.of(MapUtils.unmodifiableMap(rules));
    }

}
