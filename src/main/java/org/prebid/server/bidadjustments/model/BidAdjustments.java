package org.prebid.server.bidadjustments.model;

import lombok.Value;
import org.apache.commons.collections4.MapUtils;
import org.prebid.server.bidadjustments.BidAdjustmentRulesValidator;
import org.prebid.server.bidadjustments.BidAdjustmentsResolver;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustments;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentsRule;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class BidAdjustments {

    private static final String RULE_SCHEME =
            "%s" + BidAdjustmentsResolver.DELIMITER + "%s" + BidAdjustmentsResolver.DELIMITER + "%s";

    Map<String, List<ExtRequestBidAdjustmentsRule>> rules;

    public static BidAdjustments of(ExtRequestBidAdjustments bidAdjustments) {
        if (bidAdjustments == null) {
            return BidAdjustments.of(Collections.emptyMap());
        }

        final Map<String, List<ExtRequestBidAdjustmentsRule>> rules = new HashMap<>();

        final Map<String, Map<String, Map<String, List<ExtRequestBidAdjustmentsRule>>>> mediatypes =
                bidAdjustments.getMediatype();

        if (MapUtils.isEmpty(mediatypes)) {
            return BidAdjustments.of(Collections.emptyMap());
        }

        for (String mediatype : mediatypes.keySet()) {
            if (BidAdjustmentRulesValidator.SUPPORTED_MEDIA_TYPES.contains(mediatype)) {
                final Map<String, Map<String, List<ExtRequestBidAdjustmentsRule>>> bidders = mediatypes.get(mediatype);
                for (String bidder : bidders.keySet()) {
                    final Map<String, List<ExtRequestBidAdjustmentsRule>> deals = bidders.get(bidder);
                    for (String dealId : deals.keySet()) {
                        rules.put(RULE_SCHEME.formatted(mediatype, bidder, dealId), deals.get(dealId));
                    }
                }
            }
        }

        return BidAdjustments.of(MapUtils.unmodifiableMap(rules));
    }

}
