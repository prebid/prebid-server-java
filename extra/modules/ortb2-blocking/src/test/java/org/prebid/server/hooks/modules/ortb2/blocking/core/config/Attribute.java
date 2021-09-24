package org.prebid.server.hooks.modules.ortb2.blocking.core.config;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Builder
@Value
public class Attribute<T> {

    @JsonIgnore
    String field;

    Boolean enforceBlocks;

    Boolean blockUnknown;

    List<T> blocked;

    List<T> allowedForDeals;

    @JsonIgnore
    AttributeActionOverrides<T> actionOverrides;

    @JsonAnyGetter
    public Map<String, Object> getProperties() {
        final Map<String, Object> properties = new HashMap<>();
        properties.computeIfAbsent("block-unknown-" + field, key -> blockUnknown);
        properties.computeIfAbsent("blocked-" + field, key -> blocked);
        properties.computeIfAbsent("allowed-" + field + "-for-deals", key -> allowedForDeals);

        properties.computeIfAbsent("action-overrides", key -> actionOverrides != null
            ? actionOverrides.toBuilder()
            .field(field)
            .build()
            : null);

        return properties;
    }

    public static AttributeBuilder<String> badvBuilder() {
        return Attribute.<String>builder()
            .field("adomain");
    }

    public static AttributeBuilder<String> bcatBuilder() {
        return Attribute.<String>builder()
            .field("adv-cat");
    }

    public static AttributeBuilder<String> bappBuilder() {
        return Attribute.<String>builder()
            .field("app");
    }

    public static AttributeBuilder<Integer> btypeBuilder() {
        return Attribute.<Integer>builder()
            .field("banner-type");
    }

    public static AttributeBuilder<Integer> battrBuilder() {
        return Attribute.<Integer>builder()
            .field("banner-attr");
    }
}
