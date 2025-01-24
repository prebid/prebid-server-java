package org.prebid.server.floors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.floors.model.PriceFloorData;
import org.prebid.server.floors.model.PriceFloorField;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.floors.model.PriceFloorSchema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class PriceFloorRulesValidator {

    private static final int MODEL_WEIGHT_MAX_VALUE = 100;
    private static final int MODEL_WEIGHT_MIN_VALUE = 1;
    private static final int SKIP_RATE_MIN = 0;
    private static final int SKIP_RATE_MAX = 100;
    private static final int USE_FETCH_DATA_RATE_MIN = 0;
    private static final int USE_FETCH_DATA_RATE_MAX = 100;

    private PriceFloorRulesValidator() {
    }

    public static void validateRules(PriceFloorRules priceFloorRules, Integer maxRules, Integer maxDimensions) {

        final Integer rootSkipRate = priceFloorRules.getSkipRate();
        if (rootSkipRate != null && (rootSkipRate < SKIP_RATE_MIN || rootSkipRate > SKIP_RATE_MAX)) {
            throw new PreBidException(
                    "Price floor root skipRate must be in range(0-100), but was " + rootSkipRate);
        }

        final BigDecimal floorMin = priceFloorRules.getFloorMin();
        if (floorMin != null && floorMin.compareTo(BigDecimal.ZERO) < 0) {
            throw new PreBidException("Price floor floorMin must be positive float, but was " + floorMin);
        }

        validateRulesData(priceFloorRules.getData(), maxRules, maxDimensions);
    }

    public static void validateRulesData(PriceFloorData priceFloorData, Integer maxRules, Integer maxDimensions) {
        if (priceFloorData == null) {
            throw new PreBidException("Price floor rules data must be present");
        }

        final Integer dataSkipRate = priceFloorData.getSkipRate();
        if (dataSkipRate != null && (dataSkipRate < SKIP_RATE_MIN || dataSkipRate > SKIP_RATE_MAX)) {
            throw new PreBidException(
                    "Price floor data skipRate must be in range(0-100), but was " + dataSkipRate);
        }

        final Integer useFetchDataRate = priceFloorData.getUseFetchDataRate();
        if (useFetchDataRate != null
                && (useFetchDataRate < USE_FETCH_DATA_RATE_MIN || useFetchDataRate > USE_FETCH_DATA_RATE_MAX)) {

            throw new PreBidException(
                    "Price floor data useFetchDataRate must be in range(0-100), but was " + useFetchDataRate);
        }

        if (CollectionUtils.isEmpty(priceFloorData.getModelGroups())) {
            throw new PreBidException("Price floor rules should contain at least one model group");
        }

        priceFloorData.getModelGroups().stream()
                .filter(Objects::nonNull)
                .forEach(modelGroup -> validateModelGroup(modelGroup, maxRules, maxDimensions));
    }

    private static void validateModelGroup(PriceFloorModelGroup modelGroup, Integer maxRules, Integer maxDimensions) {
        final Integer modelWeight = modelGroup.getModelWeight();
        if (modelWeight != null
                && (modelWeight < MODEL_WEIGHT_MIN_VALUE || modelWeight > MODEL_WEIGHT_MAX_VALUE)) {

            throw new PreBidException(
                    "Price floor modelGroup modelWeight must be in range(1-100), but was " + modelWeight);

        }

        final Integer skipRate = modelGroup.getSkipRate();
        if (skipRate != null && (skipRate < SKIP_RATE_MIN || skipRate > SKIP_RATE_MAX)) {
            throw new PreBidException(
                    "Price floor modelGroup skipRate must be in range(0-100), but was " + skipRate);
        }

        final BigDecimal defaultPrice = modelGroup.getDefaultFloor();
        if (defaultPrice != null && defaultPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new PreBidException(
                    "Price floor modelGroup default must be positive float, but was " + defaultPrice);
        }

        final Map<String, BigDecimal> values = modelGroup.getValues();
        if (MapUtils.isEmpty(values)) {
            throw new PreBidException("Price floor rules values can't be null or empty, but were " + values);
        }

        if (maxRules != null && values.size() > maxRules) {
            throw new PreBidException("Price floor rules number %s exceeded its maximum number %s"
                    .formatted(values.size(), maxRules));
        }

        final List<PriceFloorField> fields = Optional.ofNullable(modelGroup.getSchema())
                .map(PriceFloorSchema::getFields)
                .orElse(null);

        if (CollectionUtils.isEmpty(fields)) {
            throw new PreBidException("Price floor dimensions can't be null or empty, but were " + fields);
        }

        if (maxDimensions != null && fields.size() > maxDimensions) {
            throw new PreBidException("Price floor schema dimensions %s exceeded its maximum number %s"
                    .formatted(fields.size(), maxDimensions));
        }
    }
}
