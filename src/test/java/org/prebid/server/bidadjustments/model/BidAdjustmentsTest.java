package org.prebid.server.bidadjustments.model;

import org.junit.jupiter.api.Test;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustments;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentsRule;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.CPM;

public class BidAdjustmentsTest {

    @Test
    public void shouldBuildRulesSet() {
        // given
        final List<ExtRequestBidAdjustmentsRule> givenRules = List.of(givenRule("1"), givenRule("2"));
        final Map<String, Map<String, List<ExtRequestBidAdjustmentsRule>>> givenRulesMap = Map.of(
                "bidderName",
                Map.of("dealId", givenRules));

        final ExtRequestBidAdjustments givenBidAdjustments = ExtRequestBidAdjustments.builder()
                .mediatype(Map.of(
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
                                        "dealId", givenRules))))
                .build();

        // when
        final BidAdjustments actual = BidAdjustments.of(givenBidAdjustments);

        // then
        final BidAdjustments expected = BidAdjustments.of(Map.of(
                "audio|bidderName|dealId", givenRules,
                "native|bidderName|dealId", givenRules,
                "video-instream|bidderName|dealId", givenRules,
                "video-outstream|bidderName|dealId", givenRules,
                "banner|bidderName|dealId", givenRules,
                "*|*|*", givenRules,
                "*|bidderName|*", givenRules,
                "*|bidderName|dealId", givenRules));

        assertThat(actual).isEqualTo(expected);

    }

    private static ExtRequestBidAdjustmentsRule givenRule(String value) {
        return ExtRequestBidAdjustmentsRule.builder()
                .adjType(CPM)
                .currency("USD")
                .value(new BigDecimal(value))
                .build();
    }
}
