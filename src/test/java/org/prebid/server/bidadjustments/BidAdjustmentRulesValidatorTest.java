package org.prebid.server.bidadjustments;

import org.junit.jupiter.api.Test;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustments;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentsRule;
import org.prebid.server.validation.ValidationException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.CPM;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.MULTIPLIER;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.STATIC;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.UNKNOWN;

public class BidAdjustmentRulesValidatorTest {

    @Test
    public void validateShouldDoNothingWhenBidAdjustmentsIsNull() throws ValidationException {
        // when & then
        BidAdjustmentRulesValidator.validate(null);
    }

    @Test
    public void validateShouldDoNothingWhenMediatypesIsEmpty() throws ValidationException {
        // when & then
        BidAdjustmentRulesValidator.validate(ExtRequestBidAdjustments.builder().build());
    }

    @Test
    public void validateShouldSkipMediatypeValidationWhenMediatypesIsNotSupported() throws ValidationException {
        // given
        final ExtRequestBidAdjustmentsRule invalidRule = ExtRequestBidAdjustmentsRule.builder()
                .value(new BigDecimal("-999"))
                .build();

        // when & then
        BidAdjustmentRulesValidator.validate(ExtRequestBidAdjustments.builder()
                .mediatype(Map.of("invalid", Map.of("bidderName", Map.of("*", List.of(invalidRule)))))
                .build());
    }

    @Test
    public void validateShouldFailWhenBiddersAreAbsent() {
        // given
        final ExtRequestBidAdjustments givenBidAdjustments = ExtRequestBidAdjustments.builder()
                .mediatype(Map.of("banner", Collections.emptyMap()))
                .build();

        // when & then
        assertThatThrownBy(() -> BidAdjustmentRulesValidator.validate(givenBidAdjustments))
                .isInstanceOf(ValidationException.class)
                .hasMessage("no bidders found in banner");
    }

    @Test
    public void validateShouldFailWhenDealsAreAbsent() {
        // given
        final ExtRequestBidAdjustments givenBidAdjustments = ExtRequestBidAdjustments.builder()
                .mediatype(Map.of("banner", Map.of("bidderName", Collections.emptyMap())))
                .build();

        // when & then
        assertThatThrownBy(() -> BidAdjustmentRulesValidator.validate(givenBidAdjustments))
                .isInstanceOf(ValidationException.class)
                .hasMessage("no deals found in banner.bidderName");
    }

    @Test
    public void validateShouldFailWhenRulesIsEmpty() {
        // given
        final Map<String, List<ExtRequestBidAdjustmentsRule>> rules = new HashMap<>();
        rules.put("*", null);

        final ExtRequestBidAdjustments givenBidAdjustments = ExtRequestBidAdjustments.builder()
                .mediatype(Map.of("banner", Map.of("bidderName", rules)))
                .build();

        // when & then
        assertThatThrownBy(() -> BidAdjustmentRulesValidator.validate(givenBidAdjustments))
                .isInstanceOf(ValidationException.class)
                .hasMessage("no bid adjustment rules found in banner.bidderName.*");
    }

    @Test
    public void validateShouldDoNothingWhenRulesAreEmpty() throws ValidationException {
        // when & then
        BidAdjustmentRulesValidator.validate(ExtRequestBidAdjustments.builder()
                .mediatype(Map.of("video_instream", Map.of("bidderName", Map.of("*", List.of()))))
                .build());
    }

    @Test
    public void validateShouldFailWhenRuleHasUnknownType() {
        // given
        final Map<String, List<ExtRequestBidAdjustmentsRule>> rules = new HashMap<>();
        rules.put("*", List.of(ExtRequestBidAdjustmentsRule.builder()
                .adjType(UNKNOWN)
                .value(BigDecimal.ONE)
                .currency("USD")
                .build()));

        final ExtRequestBidAdjustments givenBidAdjustments = ExtRequestBidAdjustments.builder()
                .mediatype(Map.of("banner", Map.of("bidderName", rules)))
                .build();

        // when & then
        assertThatThrownBy(() -> BidAdjustmentRulesValidator.validate(givenBidAdjustments))
                .isInstanceOf(ValidationException.class)
                .hasMessage("the found rule [adjtype=UNKNOWN, value=1, currency=USD] "
                        + "in banner.bidderName.* is invalid");
    }

    @Test
    public void validateShouldFailWhenCpmRuleDoesNotHaveCurrency() {
        // given
        final Map<String, List<ExtRequestBidAdjustmentsRule>> rules = new HashMap<>();
        rules.put("*", List.of(givenCpm("1", "USD"), givenCpm("1", null)));

        final ExtRequestBidAdjustments givenBidAdjustments = ExtRequestBidAdjustments.builder()
                .mediatype(Map.of("banner", Map.of("bidderName", rules)))
                .build();

        // when & then
        assertThatThrownBy(() -> BidAdjustmentRulesValidator.validate(givenBidAdjustments))
                .isInstanceOf(ValidationException.class)
                .hasMessage("the found rule [adjtype=CPM, value=1, currency=null] in banner.bidderName.* is invalid");
    }

    @Test
    public void validateShouldFailWhenCpmRuleDoesHasNegativeValue() {
        // given
        final Map<String, List<ExtRequestBidAdjustmentsRule>> rules = new HashMap<>();
        rules.put("*", List.of(givenCpm("0", "USD"), givenCpm("-1", "USD")));

        final ExtRequestBidAdjustments givenBidAdjustments = ExtRequestBidAdjustments.builder()
                .mediatype(Map.of("banner", Map.of("bidderName", rules)))
                .build();

        // when & then
        assertThatThrownBy(() -> BidAdjustmentRulesValidator.validate(givenBidAdjustments))
                .isInstanceOf(ValidationException.class)
                .hasMessage("the found rule [adjtype=CPM, value=-1, currency=USD] in banner.bidderName.* is invalid");
    }

    @Test
    public void validateShouldFailWhenCpmRuleDoesHasValueMoreThanMaxInt() {
        // given
        final Map<String, List<ExtRequestBidAdjustmentsRule>> rules = new HashMap<>();
        rules.put("*", List.of(givenCpm("0", "USD"), givenCpm("2147483647", "USD")));

        final ExtRequestBidAdjustments givenBidAdjustments = ExtRequestBidAdjustments.builder()
                .mediatype(Map.of("banner", Map.of("bidderName", rules)))
                .build();

        // when & then
        assertThatThrownBy(() -> BidAdjustmentRulesValidator.validate(givenBidAdjustments))
                .isInstanceOf(ValidationException.class)
                .hasMessage("the found rule [adjtype=CPM, value=2147483647, currency=USD] "
                        + "in banner.bidderName.* is invalid");
    }

    @Test
    public void validateShouldFailWhenStaticRuleDoesNotHaveCurrency() {
        // given
        final Map<String, List<ExtRequestBidAdjustmentsRule>> rules = new HashMap<>();
        rules.put("*", List.of(givenStatic("1", "USD"), givenStatic("1", null)));

        final ExtRequestBidAdjustments givenBidAdjustments = ExtRequestBidAdjustments.builder()
                .mediatype(Map.of("banner", Map.of("bidderName", rules)))
                .build();

        // when & then
        assertThatThrownBy(() -> BidAdjustmentRulesValidator.validate(givenBidAdjustments))
                .isInstanceOf(ValidationException.class)
                .hasMessage("the found rule [adjtype=STATIC, value=1, currency=null] "
                        + "in banner.bidderName.* is invalid");
    }

    @Test
    public void validateShouldFailWhenStaticRuleDoesHasNegativeValue() {
        // given
        final Map<String, List<ExtRequestBidAdjustmentsRule>> rules = new HashMap<>();
        rules.put("*", List.of(givenStatic("0", "USD"), givenStatic("-1", "USD")));

        final ExtRequestBidAdjustments givenBidAdjustments = ExtRequestBidAdjustments.builder()
                .mediatype(Map.of("banner", Map.of("bidderName", rules)))
                .build();

        // when & then
        assertThatThrownBy(() -> BidAdjustmentRulesValidator.validate(givenBidAdjustments))
                .isInstanceOf(ValidationException.class)
                .hasMessage("the found rule [adjtype=STATIC, value=-1, currency=USD] "
                        + "in banner.bidderName.* is invalid");
    }

    @Test
    public void validateShouldFailWhenStaticRuleDoesHasValueMoreThanMaxInt() {
        // given
        final Map<String, List<ExtRequestBidAdjustmentsRule>> rules = new HashMap<>();
        rules.put("*", List.of(givenStatic("0", "USD"), givenStatic("2147483647", "USD")));

        final ExtRequestBidAdjustments givenBidAdjustments = ExtRequestBidAdjustments.builder()
                .mediatype(Map.of("banner", Map.of("bidderName", rules)))
                .build();

        // when & then
        assertThatThrownBy(() -> BidAdjustmentRulesValidator.validate(givenBidAdjustments))
                .isInstanceOf(ValidationException.class)
                .hasMessage("the found rule [adjtype=STATIC, value=2147483647, currency=USD] "
                        + "in banner.bidderName.* is invalid");
    }

    @Test
    public void validateShouldFailWhenMultiplierRuleDoesHasNegativeValue() {
        // given
        final Map<String, List<ExtRequestBidAdjustmentsRule>> rules = new HashMap<>();
        rules.put("*", List.of(givenMultiplier("0"), givenMultiplier("-1")));

        final ExtRequestBidAdjustments givenBidAdjustments = ExtRequestBidAdjustments.builder()
                .mediatype(Map.of("banner", Map.of("bidderName", rules)))
                .build();

        // when & then
        assertThatThrownBy(() -> BidAdjustmentRulesValidator.validate(givenBidAdjustments))
                .isInstanceOf(ValidationException.class)
                .hasMessage("the found rule [adjtype=MULTIPLIER, value=-1, currency=null] "
                        + "in banner.bidderName.* is invalid");
    }

    @Test
    public void validateShouldFailWhenMultiplierRuleDoesHasValueMoreThan100() {
        // given
        final Map<String, List<ExtRequestBidAdjustmentsRule>> rules = new HashMap<>();
        rules.put("*", List.of(givenMultiplier("0"), givenMultiplier("100")));

        final ExtRequestBidAdjustments givenBidAdjustments = ExtRequestBidAdjustments.builder()
                .mediatype(Map.of("banner", Map.of("bidderName", rules)))
                .build();

        // when & then
        assertThatThrownBy(() -> BidAdjustmentRulesValidator.validate(givenBidAdjustments))
                .isInstanceOf(ValidationException.class)
                .hasMessage("the found rule [adjtype=MULTIPLIER, value=100, currency=null] "
                        + "in banner.bidderName.* is invalid");
    }

    @Test
    public void validateShouldDoNothingWhenAllRulesAreValid() throws ValidationException {
        // given
        final List<ExtRequestBidAdjustmentsRule> givenRules = List.of(
                givenMultiplier("1"),
                givenCpm("2", "USD"),
                givenStatic("3", "EUR"));

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

        //when & then
        BidAdjustmentRulesValidator.validate(givenBidAdjustments);
    }

    private static ExtRequestBidAdjustmentsRule givenStatic(String value, String currency) {
        return ExtRequestBidAdjustmentsRule.builder()
                .adjType(STATIC)
                .currency(currency)
                .value(new BigDecimal(value))
                .build();
    }

    private static ExtRequestBidAdjustmentsRule givenCpm(String value, String currency) {
        return ExtRequestBidAdjustmentsRule.builder()
                .adjType(CPM)
                .currency(currency)
                .value(new BigDecimal(value))
                .build();
    }

    private static ExtRequestBidAdjustmentsRule givenMultiplier(String value) {
        return ExtRequestBidAdjustmentsRule.builder()
                .adjType(MULTIPLIER)
                .value(new BigDecimal(value))
                .build();
    }
}
