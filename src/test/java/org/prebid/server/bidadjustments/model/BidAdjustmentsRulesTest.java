package org.prebid.server.bidadjustments.model;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.CPM;

public class BidAdjustmentsRulesTest {

    @Test
    public void shouldBuildRulesSet() {
        // given
        final List<BidAdjustmentsRule> givenRules = List.of(givenRule("1"), givenRule("2"));
        final Map<String, Map<String, List<BidAdjustmentsRule>>> givenRulesMap = Map.of(
                "bidderName",
                Map.of("dealId", givenRules));

        final BidAdjustments givenBidAdjustments = BidAdjustments.of(
                Map.of(
                        "audio", givenRulesMap,
                        "native", givenRulesMap,
                        "video-instream", givenRulesMap,
                        "video-outstream", givenRulesMap,
                        "banner", givenRulesMap,
                        "video", givenRulesMap,
                        "unknown", givenRulesMap,
                        "*", Map.of(
                                "*", Map.of("*", givenRules),
                                "bidderName", Map.of(
                                        "*", givenRules,
                                        "dealId", givenRules))));

        // when
        final BidAdjustmentsRules actual = BidAdjustmentsRules.of(givenBidAdjustments);

        // then
        final BidAdjustmentsRules expected = BidAdjustmentsRules.of(new CaseInsensitiveMap<>(Map.of(
                "audio|bidderName|dealId", givenRules,
                "native|bidderName|dealId", givenRules,
                "video-instream|bidderName|dealId", givenRules,
                "video-outstream|bidderName|dealId", givenRules,
                "banner|bidderName|dealId", givenRules,
                "*|*|*", givenRules,
                "*|bidderName|*", givenRules,
                "*|bidderName|dealId", givenRules)));

        assertThat(actual).isEqualTo(expected);
    }

    private static BidAdjustmentsRule givenRule(String value) {
        return BidAdjustmentsRule.builder()
                .adjType(CPM)
                .currency("USD")
                .value(new BigDecimal(value))
                .build();
    }

}
