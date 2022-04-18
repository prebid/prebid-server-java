package org.prebid.server.floors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.floors.model.PriceFloorData;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorRules;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public class PriceFloorRulesValidator {

    private static final int MODEL_WEIGHT_MAX_VALUE = 100;
    private static final int MODEL_WEIGHT_MIN_VALUE = 1;
    private static final int SKIP_RATE_MIN = 0;
    private static final int SKIP_RATE_MAX = 100;

    private PriceFloorRulesValidator() {
    }

    public static void validate(PriceFloorRules priceFloorRules, Integer maxRules) {

        final Integer rootSkipRate = priceFloorRules.getSkipRate();
        if (rootSkipRate != null && (rootSkipRate < SKIP_RATE_MIN || rootSkipRate > SKIP_RATE_MAX)) {
            throw new PreBidException(String.format("Price floor root skipRate "
                    + "must be in range(0-100), but was %s", rootSkipRate));
        }

        final BigDecimal floorMin = priceFloorRules.getFloorMin();
        if (floorMin != null && floorMin.compareTo(BigDecimal.ZERO) < 0) {
            throw new PreBidException(String.format("Price floor floorMin "
                    + "must be positive float, but was %s", floorMin));
        }

        final PriceFloorData data = priceFloorRules.getData();
        if (data == null) {
            throw new PreBidException("Price floor rules data must be present");
        }

        final Integer dataSkipRate = data.getSkipRate();
        if (dataSkipRate != null && (dataSkipRate < SKIP_RATE_MIN || dataSkipRate > SKIP_RATE_MAX)) {
            throw new PreBidException(String.format("Price floor data skipRate "
                    + "must be in range(0-100), but was %s", dataSkipRate));
        }

        if (CollectionUtils.isEmpty(data.getModelGroups())) {
            throw new PreBidException("Price floor rules should contain at least one model group");
        }

        data.getModelGroups().stream()
                .filter(Objects::nonNull)
                .forEach(modelGroup -> validateModelGroup(modelGroup, maxRules));
    }

    private static void validateModelGroup(PriceFloorModelGroup modelGroup, Integer maxRules) {
        final Integer modelWeight = modelGroup.getModelWeight();
        if (modelWeight != null
                && (modelWeight < MODEL_WEIGHT_MIN_VALUE || modelWeight > MODEL_WEIGHT_MAX_VALUE)) {

            throw new PreBidException(String.format("Price floor modelGroup modelWeight "
                    + "must be in range(1-100), but was %s", modelWeight));

        }

        final Integer skipRate = modelGroup.getSkipRate();
        if (skipRate != null && (skipRate < SKIP_RATE_MIN || skipRate > SKIP_RATE_MAX)) {
            throw new PreBidException(String.format("Price floor modelGroup skipRate "
                    + "must be in range(0-100), but was %s", skipRate));
        }

        final BigDecimal defaultPrice = modelGroup.getDefaultFloor();
        if (defaultPrice != null && defaultPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new PreBidException(String.format("Price floor modelGroup default "
                    + "must be positive float, but was %s", defaultPrice));
        }

        final Map<String, BigDecimal> values = modelGroup.getValues();
        if (MapUtils.isEmpty(values)) {
            throw new PreBidException(String.format("Price floor rules values can't be null or empty, but were %s",
                    values));
        }

        if (maxRules != null && values.size() > maxRules) {
            throw new PreBidException(String.format("Price floor rules number %s exceeded its maximum number %s",
                    values.size(), maxRules));
        }
    }
}
