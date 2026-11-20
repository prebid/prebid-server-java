package org.prebid.server.hooks.modules.intentiq.identity.v1.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.prebid.server.hooks.modules.intentiq.identity.model.config.IntentiqIdentityProperties;
import org.prebid.server.json.JsonMerger;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolves effective module properties for a request by merging the account-level config
 * (from {@code AuctionInvocationContext.accountConfig()}) over the host-level (global) properties.
 * Mirrors the optable-targeting module's resolver.
 */
public class ConfigResolver {

    private final ObjectMapper mapper;
    private final JsonMerger jsonMerger;
    private final IntentiqIdentityProperties globalProperties;
    private final JsonNode globalPropertiesNode;

    public ConfigResolver(ObjectMapper mapper, JsonMerger jsonMerger, IntentiqIdentityProperties globalProperties) {
        this.mapper = Objects.requireNonNull(mapper);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
        this.globalProperties = Objects.requireNonNull(globalProperties);
        this.globalPropertiesNode = Objects.requireNonNull(mapper.valueToTree(globalProperties));
    }

    public IntentiqIdentityProperties resolve(ObjectNode accountConfig) {
        // With host-level-only config (no account override) accountConfig is null; JsonMergePatch
        // rejects a null patch ("input cannot be null"), so short-circuit to the global properties.
        if (accountConfig == null || accountConfig.isEmpty()) {
            return globalProperties;
        }
        final JsonNode merged = jsonMerger.merge(accountConfig, globalPropertiesNode);
        return parse(merged).orElse(globalProperties);
    }

    private Optional<IntentiqIdentityProperties> parse(JsonNode node) {
        try {
            return Optional.ofNullable(node)
                    .filter(it -> !it.isEmpty())
                    .map(it -> mapper.convertValue(it, IntentiqIdentityProperties.class));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
