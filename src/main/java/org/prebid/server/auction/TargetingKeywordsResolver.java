package org.prebid.server.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
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

    public static final String IMP_PREFIX = "imp.";
    public static final String SEATBID_BID_PREFIX = "seatbid.bid.";
    public static final String BIDDER_MACRO = "{{BIDDER}}";

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

    public Map<String, String> resolve(Bid bid, String bidder) {
        final Map<String, String> result = new HashMap<>(staticAndRequestKeywords);
        result.putAll(resolveImpRequestKeywords(bid));
        result.putAll(resolveResponseKeywords(bid, bidder));

        return result;
    }

    private Map<Source, List<ExtRequestPrebidAdservertargetingRule>> rulesBySource() {
        final ExtRequest extRequest = bidRequest.getExt();
        final List<ExtRequestPrebidAdservertargetingRule> rules =
                get(get(extRequest, ExtRequest::getPrebid), ExtRequestPrebid::getAdservertargeting);

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

        return rulesBySource.getOrDefault(Source.bidresponse, Collections.emptyList()).stream()
                .filter(TargetingKeywordsResolver::hasSeatbidBidPath)
                .collect(Collectors.toList());
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
                    Function.identity(),
                    Function.identity());
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
                        impNode,
                        impRequestRules,
                        value -> StringUtils.substringAfter(value, IMP_PREFIX),
                        Function.identity());
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

    private Map<String, String> resolveResponseKeywords(Bid bid, String bidder) {
        if (!responseRules.isEmpty()) {
            return lookupValues(
                    mapper.mapper().valueToTree(bid),
                    responseRules,
                    value -> StringUtils.substringAfter(value, SEATBID_BID_PREFIX),
                    key -> StringUtils.replace(key, BIDDER_MACRO, bidder));
        }

        return Collections.emptyMap();
    }

    private static boolean isValid(ExtRequestPrebidAdservertargetingRule rule) {
        return StringUtils.isNotBlank(rule.getKey())
                && StringUtils.isNotBlank(rule.getValue())
                && rule.getSource() != null;
    }

    private static boolean hasImpPath(ExtRequestPrebidAdservertargetingRule rule) {
        return rule.getValue().startsWith(IMP_PREFIX);
    }

    private static boolean hasSeatbidBidPath(ExtRequestPrebidAdservertargetingRule rule) {
        return rule.getValue().startsWith(SEATBID_BID_PREFIX);
    }

    private static Map<String, String> lookupValues(
            JsonNode node,
            List<ExtRequestPrebidAdservertargetingRule> rules,
            Function<String, String> pathValueMapper,
            Function<String, String> keyMapper) {

        final Map<String, String> result = new HashMap<>();

        for (final ExtRequestPrebidAdservertargetingRule rule : rules) {
            final String lookupResult = lookupValue(node, pathValueMapper.apply(rule.getValue()));
            if (StringUtils.isNotBlank(lookupResult)) {
                result.put(keyMapper.apply(rule.getKey()), lookupResult);
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

    private static <T, U> U get(T target, Function<T, U> getter) {
        return target != null ? getter.apply(target) : null;
    }
}
