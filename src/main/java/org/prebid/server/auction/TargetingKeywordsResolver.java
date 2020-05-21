package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAdservertargetingRule;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAdservertargetingRule.Source;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TargetingKeywordsResolver {

    private static final Logger logger = LoggerFactory.getLogger(TargetingKeywordsResolver.class);

    public static final String IMP_PREFIX = "imp.";

    private final BidRequest bidRequest;
    private final JacksonMapper mapper;

    private final Map<String, String> staticAndRequestKeywords;

    private TargetingKeywordsResolver(BidRequest bidRequest, JacksonMapper mapper) {
        this.bidRequest = Objects.requireNonNull(bidRequest);
        this.mapper = Objects.requireNonNull(mapper);

        final Map<Source, List<ExtRequestPrebidAdservertargetingRule>> rulesBySource = extractRulesBySource();

        this.staticAndRequestKeywords = resolveStaticAndRequestKeywords(rulesBySource);
    }

    public static TargetingKeywordsResolver create(BidRequest bidRequest, JacksonMapper mapper) {
        return new TargetingKeywordsResolver(bidRequest, mapper);
    }

    public Map<String, String> resolve(Bid bid) {
        return staticAndRequestKeywords;
    }

    private Map<Source, List<ExtRequestPrebidAdservertargetingRule>> extractRulesBySource() {
        final ExtBidRequest extRequest = parseExt(bidRequest.getExt());
        final List<ExtRequestPrebidAdservertargetingRule> rules =
                get(get(extRequest, ExtBidRequest::getPrebid), ExtRequestPrebid::getAdservertargeting);

        return ObjectUtils.<List<ExtRequestPrebidAdservertargetingRule>>defaultIfNull(rules, Collections.emptyList())
                .stream()
                .filter(TargetingKeywordsResolver::isValid)
                .collect(Collectors.groupingBy(ExtRequestPrebidAdservertargetingRule::getSource));
    }

    private static boolean isValid(ExtRequestPrebidAdservertargetingRule rule) {
        return StringUtils.isNotBlank(rule.getKey())
                && StringUtils.isNotBlank(rule.getValue())
                && rule.getSource() != null;
    }

    private Map<String, String> resolveStaticAndRequestKeywords(
            Map<Source, List<ExtRequestPrebidAdservertargetingRule>> rulesBySource) {

        final Map<String, String> result = new HashMap<>(resolveStaticKeywords(rulesBySource));
        result.putAll(resolveRequestKeywords(rulesBySource));

        return result;
    }

    private static Map<String, String> resolveStaticKeywords(
            Map<Source, List<ExtRequestPrebidAdservertargetingRule>> rulesBySource) {

        return rulesBySource.getOrDefault(Source.xStatic, Collections.emptyList()).stream()
                .collect(Collectors.toMap(
                        ExtRequestPrebidAdservertargetingRule::getKey,
                        ExtRequestPrebidAdservertargetingRule::getValue,
                        (value1, value2) -> value2));
    }

    private Map<String, String> resolveRequestKeywords(
            Map<Source, List<ExtRequestPrebidAdservertargetingRule>> rulesBySource) {

        final Map<String, String> result = new HashMap<>();

        final List<ExtRequestPrebidAdservertargetingRule> requestRules =
                rulesBySource.getOrDefault(Source.bidrequest, Collections.emptyList()).stream()
                        .filter(rule -> !hasImpPath(rule))
                        .collect(Collectors.toList());

        if (!requestRules.isEmpty()) {
            final JsonNode bidRequestNode = mapper.mapper().valueToTree(bidRequest);

            for (final ExtRequestPrebidAdservertargetingRule requestRule : requestRules) {
                final String path = toPath(requestRule.getValue());
                final String lookupResult = bidRequestNode.at(path).asText();
                if (StringUtils.isNotBlank(lookupResult)) {
                    result.put(requestRule.getKey(), lookupResult);
                }
            }
        }

        return result;
    }

    private static boolean hasImpPath(ExtRequestPrebidAdservertargetingRule rule) {
        return rule.getValue().startsWith(IMP_PREFIX);
    }

    private String toPath(String value) {
        return String.format("/%s", value.replaceAll("\\.", "/"));
    }

    public ExtBidRequest parseExt(ObjectNode ext) {
        try {
            return mapper.mapper().treeToValue(ext, ExtBidRequest.class);
        } catch (JsonProcessingException e) {
            logger.warn("Error occurred while parsing request extension", e);
            return null;
        }
    }

    private static <T, U> U get(T target, Function<T, U> getter) {
        return target != null ? getter.apply(target) : null;
    }
}
