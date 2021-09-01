package org.prebid.server.hooks.modules.ortb2.blocking.core.config;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Builder(toBuilder = true)
@Value
public class AttributeActionOverrides<T> {

    @JsonIgnore
    String field;

    List<ArrayOverride<T>> blocked;

    List<BooleanOverride> enforceBlocks;

    List<BooleanOverride> blockUnknown;

    List<AllowedForDealsOverride<T>> allowedForDeals;

    @JsonAnyGetter
    public Map<String, Object> getProperties() {
        final Map<String, Object> properties = new HashMap<>();
        properties.computeIfAbsent("block-unknown-" + field, key -> blockUnknown);
        properties.computeIfAbsent("blocked-" + field, key -> blocked);
        properties.computeIfAbsent("allowed-" + field + "-for-deals", key -> allowedForDeals);

        return properties;
    }

    public static <T> AttributeActionOverrides<T> blocked(List<ArrayOverride<T>> blocked) {
        return AttributeActionOverrides.<T>builder()
            .blocked(blocked)
            .build();
    }

    public static <T> AttributeActionOverrides<T> blockUnknown(List<BooleanOverride> blockUnknown) {
        return AttributeActionOverrides.<T>builder()
            .blockUnknown(blockUnknown)
            .build();
    }

    public static <T> AttributeActionOverrides<T> blockFlags(
        List<BooleanOverride> enforceBlocks,
        List<BooleanOverride> blockUnknown) {

        return AttributeActionOverrides.<T>builder()
            .enforceBlocks(enforceBlocks)
            .blockUnknown(blockUnknown)
            .build();
    }

    public static <T> AttributeActionOverrides<T> response(
        List<BooleanOverride> enforceBlocks,
        List<BooleanOverride> blockUnknown,
        List<AllowedForDealsOverride<T>> allowedForDeals) {

        return AttributeActionOverrides.<T>builder()
            .enforceBlocks(enforceBlocks)
            .blockUnknown(blockUnknown)
            .allowedForDeals(allowedForDeals)
            .build();
    }

    public static <T> AttributeActionOverrides<T> allowedForDeals(List<AllowedForDealsOverride<T>> allowedForDeals) {

        return AttributeActionOverrides.<T>builder()
            .allowedForDeals(allowedForDeals)
            .build();
    }
}
