package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.optable.targeting.model.Id;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.hooks.modules.optable.targeting.model.Query;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class QueryBuilder {

    private QueryBuilder() {
    }

    public static Query build(List<Id> ids, OptableAttributes optableAttributes, String idPrefixOrder) {
        if (CollectionUtils.isEmpty(ids) && CollectionUtils.isEmpty(optableAttributes.getIps())) {
            return null;
        }

        return Query.of(buildIdsString(ids, idPrefixOrder), buildAttributesString(optableAttributes));
    }

    private static String buildIdsString(List<Id> ids, String idPrefixOrder) {
        if (CollectionUtils.isEmpty(ids)) {
            return StringUtils.EMPTY;
        }

        final List<Id> reorderedIds = reorderIds(ids, idPrefixOrder);

        final StringBuilder sb = new StringBuilder();
        for (Id id : reorderedIds) {
            sb.append("&id=");
            sb.append(URLEncoder.encode(
                    "%s:%s".formatted(id.getName(), id.getValue()),
                    StandardCharsets.UTF_8));
        }

        return sb.toString();
    }

    private static List<Id> reorderIds(List<Id> ids, String idPrefixOrder) {
        if (StringUtils.isEmpty(idPrefixOrder)) {
            return ids;
        }

        final String[] prefixOrder = idPrefixOrder.split(",");
        final Map<String, Integer> prefixToPriority = IntStream.range(0, prefixOrder.length).boxed()
                .collect(Collectors.toMap(i -> prefixOrder[i], Function.identity()));

        final List<Id> orderedIds = new ArrayList<>(ids);
        orderedIds.sort(Comparator.comparing(item -> prefixToPriority.getOrDefault(item.getName(), Integer.MAX_VALUE)));

        return orderedIds;
    }

    private static String buildAttributesString(OptableAttributes optableAttributes) {
        final StringBuilder sb = new StringBuilder();

        Optional.ofNullable(optableAttributes.getGdprConsent())
                .ifPresent(consent -> sb.append("&gdpr_consent=").append(consent));
        sb.append("&gdpr=").append(optableAttributes.isGdprApplies() ? 1 : 0);

        Optional.ofNullable(optableAttributes.getGpp())
                .ifPresent(gpp -> sb.append("&gpp=").append(gpp));
        Optional.ofNullable(optableAttributes.getGppSid())
                .filter(Predicate.not(Collection::isEmpty))
                .ifPresent(gppSids -> sb.append("&gpp_sid=").append(gppSids.stream().findFirst()));

        Optional.ofNullable(optableAttributes.getTimeout())
                .ifPresent(timeout -> sb.append("&timeout=").append(timeout).append("ms"));

        sb.append("&osdk=").append(optableAttributes.getRequestSource());

        return sb.toString();
    }
}
