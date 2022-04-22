package org.prebid.server.floors;

import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.floors.model.PriceFloorData;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorRules;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class PriceFloorRulesValidatorTest extends VertxTest {

    @Test
    public void validateShouldThrowExceptionOnInvalidRootSkipRateWhenPresent() {
        // given
        final PriceFloorRules priceFloorRules = givenPriceFloorRules(rulesBuilder -> rulesBuilder.skipRate(-1));

        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> PriceFloorRulesValidator.validate(priceFloorRules, 100))
                .withMessage("Price floor root skipRate must be in range(0-100), but was -1");
    }

    @Test
    public void validateShouldThrowExceptionWhenFloorMinPresentAndLessThanZero() {
        // given
        final PriceFloorRules priceFloorRules = givenPriceFloorRules(
                rulesBuilder -> rulesBuilder.floorMin(BigDecimal.valueOf(-1)));

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> PriceFloorRulesValidator.validate(priceFloorRules, 100))
                .withMessage("Price floor floorMin must be positive float, but was -1");
    }

    @Test
    public void validateShouldThrowExceptionWhenDataIsAbsent() {
        // given
        final PriceFloorRules priceFloorRules = givenPriceFloorRules(rulesBuilder -> rulesBuilder.data(null));

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> PriceFloorRulesValidator.validate(priceFloorRules, 100))
                .withMessage("Price floor rules data must be present");
    }

    @Test
    public void validateShouldThrowExceptionOnInvalidDataSkipRateWhenPresent() {
        // given
        final PriceFloorRules priceFloorRules = givenPriceFloorRulesWithData(dataBuilder -> dataBuilder.skipRate(-1));

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> PriceFloorRulesValidator.validate(priceFloorRules, 100))
                .withMessage("Price floor data skipRate must be in range(0-100), but was -1");
    }

    @Test
    public void validateShouldThrowExceptionOnAbsentDataModelGroups() {
        // given
        final PriceFloorRules priceFloorRules = givenPriceFloorRulesWithData(
                dataBuilder -> dataBuilder.modelGroups(null));

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> PriceFloorRulesValidator.validate(priceFloorRules, 100))
                .withMessage("Price floor rules should contain at least one model group");
    }

    @Test
    public void validateShouldThrowExceptionOnEmptyDataModelGroups() {
        // given
        final PriceFloorRules priceFloorRules = givenPriceFloorRulesWithData(
                dataBuilder -> dataBuilder.modelGroups(Collections.emptyList()));

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> PriceFloorRulesValidator.validate(priceFloorRules, 100))
                .withMessage("Price floor rules should contain at least one model group");
    }

    @Test
    public void validateShouldThrowExceptionOnInvalidDataModelGroupModelWeightWhenPresent() {
        // given
        final PriceFloorRules priceFloorRules = givenPriceFloorRulesWithDataModelGroups(
                modelGroupBuilder -> modelGroupBuilder.modelWeight(-1));

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> PriceFloorRulesValidator.validate(priceFloorRules, 100))
                .withMessage("Price floor modelGroup modelWeight must be in range(1-100), but was -1");
    }

    @Test
    public void validateShouldThrowExceptionOnInvalidDataModelGroupSkipRateWhenPresent() {
        // given
        final PriceFloorRules priceFloorRules = givenPriceFloorRulesWithDataModelGroups(
                modelGroupBuilder -> modelGroupBuilder.skipRate(-1));

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> PriceFloorRulesValidator.validate(priceFloorRules, 100))
                .withMessage("Price floor modelGroup skipRate must be in range(0-100), but was -1");
    }

    @Test
    public void validateShouldThrowExceptionOnInvalidDataModelGroupDefaultFloorWhenPresent() {
        // given
        final PriceFloorRules priceFloorRules = givenPriceFloorRulesWithDataModelGroups(
                modelGroupBuilder -> modelGroupBuilder.defaultFloor(BigDecimal.valueOf(-1)));

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> PriceFloorRulesValidator.validate(priceFloorRules, 100))
                .withMessage("Price floor modelGroup default must be positive float, but was -1");
    }

    @Test
    public void validateShouldThrowExceptionOnEmptyModelGroupValues() {
        // given
        final PriceFloorRules priceFloorRules = givenPriceFloorRulesWithDataModelGroups(
                PriceFloorModelGroup.PriceFloorModelGroupBuilder::clearValues);

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> PriceFloorRulesValidator.validate(priceFloorRules, 100))
                .withMessage("Price floor rules values can't be null or empty, but were {}");
    }

    @Test
    public void validateShouldThrowExceptionWhenModelGroupValuesSizeGreaterThanMaxRules() {
        // given
        final Map<String, BigDecimal> modelGroupValues = Map.of(
                "v1", BigDecimal.TEN,
                "v2", BigDecimal.TEN);

        final PriceFloorRules priceFloorRules = givenPriceFloorRulesWithDataModelGroups(
                modelGroupBuilder -> modelGroupBuilder.clearValues().values(modelGroupValues));

        final int maxRules = modelGroupValues.size() - 1;

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> PriceFloorRulesValidator.validate(priceFloorRules, maxRules))
                .withMessage(
                        "Price floor rules number %s exceeded its maximum number %s",
                        modelGroupValues.size(),
                        maxRules);
    }

    private static PriceFloorRules givenPriceFloorRulesWithDataModelGroups(
            UnaryOperator<PriceFloorModelGroup.PriceFloorModelGroupBuilder>... modelGroupBuilders) {

        final PriceFloorModelGroup.PriceFloorModelGroupBuilder validModelGroupBuilder =
                PriceFloorModelGroup.builder()
                        .modelWeight(10)
                        .skipRate(10)
                        .defaultFloor(BigDecimal.TEN)
                        .values(Map.of("value", BigDecimal.TEN));

        final List<PriceFloorModelGroup> modelGroups = Arrays.stream(modelGroupBuilders)
                .map(modelGroupBuilder -> modelGroupBuilder.apply(validModelGroupBuilder).build())
                .collect(Collectors.toList());

        return givenPriceFloorRulesWithData(dataBuilder -> dataBuilder.modelGroups(modelGroups));
    }

    private static PriceFloorRules givenPriceFloorRulesWithData(
            UnaryOperator<PriceFloorData.PriceFloorDataBuilder> dataBuilder) {

        return givenPriceFloorRules(UnaryOperator.identity(), dataBuilder);
    }

    private static PriceFloorRules givenPriceFloorRules(
            UnaryOperator<PriceFloorRules.PriceFloorRulesBuilder> rulesBuilder) {

        return givenPriceFloorRules(rulesBuilder, UnaryOperator.identity());
    }

    private static PriceFloorRules givenPriceFloorRules(
            UnaryOperator<PriceFloorRules.PriceFloorRulesBuilder> rulesBuilder,
            UnaryOperator<PriceFloorData.PriceFloorDataBuilder> dataBuilder) {

        final PriceFloorRules priceFloorRules = PriceFloorRules.builder()
                .skipRate(10)
                .floorMin(BigDecimal.TEN)
                .data(dataBuilder.apply(PriceFloorData.builder()).build())
                .build();

        return rulesBuilder.apply(priceFloorRules.toBuilder()).build();
    }
}
