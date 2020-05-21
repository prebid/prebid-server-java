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
    private final List<ExtRequestPrebidAdservertargetingRule> impRequestRules;
    private final List<ExtRequestPrebidAdservertargetingRule> responseRules;

    private TargetingKeywordsResolver(BidRequest bidRequest, JacksonMapper mapper) {
        this.bidRequest = Objects.requireNonNull(bidRequest);
        this.mapper = Objects.requireNonNull(mapper);

        final Map<Source, List<ExtRequestPrebidAdservertargetingRule>> rulesBySource = rulesBySource();

        this.impRequestRules = impRequestRules(rulesBySource);
        this.responseRules = responseRules(rulesBySource);
        this.staticAndRequestKeywords = resolveStaticAndRequestKeywords(rulesBySource);
    }

    public static TargetingKeywordsResolver create(BidRequest bidRequest, JacksonMapper mapper) {
        return new TargetingKeywordsResolver(bidRequest, mapper);
    }

    public Map<String, String> resolve(Bid bid) {
        final Map<String, String> result = new HashMap<>(staticAndRequestKeywords);
        result.putAll(resolveImpRequestKeywords(bid));

        return result;
    }

    private Map<Source, List<ExtRequestPrebidAdservertargetingRule>> rulesBySource() {
        final ExtBidRequest extRequest = parseExt(bidRequest.getExt());
        final List<ExtRequestPrebidAdservertargetingRule> rules =
                get(get(extRequest, ExtBidRequest::getPrebid), ExtRequestPrebid::getAdservertargeting);

        return ObjectUtils.<List<ExtRequestPrebidAdservertargetingRule>>defaultIfNull(rules, Collections.emptyList())
                .stream()
                .filter(TargetingKeywordsResolver::isValid)
                .collect(Collectors.groupingBy(ExtRequestPrebidAdservertargetingRule::getSource));
    }

    private static List<ExtRequestPrebidAdservertargetingRule> impRequestRules(
            Map<Source, List<ExtRequestPrebidAdservertargetingRule>> rulesBySource) {

        return rulesBySource.getOrDefault(Source.bidrequest, Collections.emptyList()).stream()
                .filter(TargetingKeywordsResolver::hasImpPath)
                .collect(Collectors.toList());
    }

    private static List<ExtRequestPrebidAdservertargetingRule> responseRules(
            Map<Source, List<ExtRequestPrebidAdservertargetingRule>> rulesBySource) {

        return rulesBySource.getOrDefault(Source.bidresponse, Collections.emptyList());
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

        final List<ExtRequestPrebidAdservertargetingRule> requestRules = requestRules(rulesBySource);

        if (!requestRules.isEmpty()) {
            return lookupValues(
                    mapper.mapper().valueToTree(bidRequest),
                    requestRules,
                    ExtRequestPrebidAdservertargetingRule::getValue);
        }

        return Collections.emptyMap();
    }

    private static List<ExtRequestPrebidAdservertargetingRule> requestRules(
            Map<Source, List<ExtRequestPrebidAdservertargetingRule>> rulesBySource) {

        return rulesBySource.getOrDefault(Source.bidrequest, Collections.emptyList()).stream()
                .filter(rule -> !hasImpPath(rule))
                .collect(Collectors.toList());
    }

    private Map<String, String> resolveImpRequestKeywords(Bid bid) {
        if (!impRequestRules.isEmpty()) {
            final JsonNode impNode = locateImp(bid);

            if (impNode != null) {
                return lookupValues(
                        impNode, impRequestRules, rule -> rule.getValue().replaceFirst(IMP_PREFIX, StringUtils.EMPTY));
            }
        }

        return Collections.emptyMap();
    }

    private JsonNode locateImp(Bid bid) {
        final String impid = bid.getImpid();
        if (StringUtils.isBlank(impid)) {
            return null;
        }

        return bidRequest.getImp().stream()
                .filter(imp -> Objects.equals(imp.getId(), impid))
                .findFirst()
                .<JsonNode>map(imp -> mapper.mapper().valueToTree(imp))
                .orElse(null);
    }

    private static boolean isValid(ExtRequestPrebidAdservertargetingRule rule) {
        return StringUtils.isNotBlank(rule.getKey())
                && StringUtils.isNotBlank(rule.getValue())
                && rule.getSource() != null;
    }

    private static boolean hasImpPath(ExtRequestPrebidAdservertargetingRule rule) {
        return rule.getValue().startsWith(IMP_PREFIX);
    }

    private static Map<String, String> lookupValues(
            JsonNode node,
            List<ExtRequestPrebidAdservertargetingRule> rules,
            Function<ExtRequestPrebidAdservertargetingRule, String> pathValueMapper) {

        final Map<String, String> result = new HashMap<>();

        for (final ExtRequestPrebidAdservertargetingRule rule : rules) {
            final String lookupResult = lookupValue(node, pathValueMapper.apply(rule));
            if (StringUtils.isNotBlank(lookupResult)) {
                result.put(rule.getKey(), lookupResult);
            }
        }

        return result;
    }

    private static String lookupValue(JsonNode impNode, String value) {
        return impNode.at(toPath(value)).asText();
    }

    private static String toPath(String value) {
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
