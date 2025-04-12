package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.optable.targeting.model.Id;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class QueryBuilder {

    public String build(List<Id> ids, OptableAttributes optableAttributes, String idPrefixOrder) {
        if (CollectionUtils.isEmpty(ids)) {
            return null;
        }

        final StringBuilder sb = new StringBuilder();
        final List<Id> reorderedIds = reorderIds(ids, idPrefixOrder);
        if (CollectionUtils.isNotEmpty(reorderedIds)) {
            buildQueryString(sb, reorderedIds);
        }
        addAttributes(sb, optableAttributes);

        return sb.toString();
    }

    private List<Id> reorderIds(List<Id> ids, String idPrefixOrder) {
        if (!StringUtils.isEmpty(idPrefixOrder)) {
            final int lastIndex = ids.size() - 1;
            final List<String> order = Stream.of(idPrefixOrder.split(",", -1)).toList();
            final List<Id> orderedIds = new ArrayList<>(ids);

            orderedIds.sort(Comparator.comparing(item -> checkOrder(item, order, lastIndex)));

            return orderedIds;
        }
        return ids;
    }

    private int checkOrder(Id item, List<String> order, int lastIndex) {
        int value = order.indexOf(item.getName());
        if (value == -1) {
            value = lastIndex;
        }
        return value;
    }

    private void addAttributes(StringBuilder sb, OptableAttributes optableAttributes) {
        Optional.ofNullable(optableAttributes.getGdprConsent()).ifPresent(consent ->
                sb.append("&gdpr_consent=").append(consent));
        Optional.of(optableAttributes.isGdprApplies()).ifPresent(applies ->
                sb.append("&gdpr=").append(applies ? 1 : 0));
        Optional.ofNullable(optableAttributes.getGpp()).ifPresent(tcf ->
                sb.append("&gpp=").append(tcf));
        Optional.ofNullable(optableAttributes.getGppSid()).ifPresent(gppSids -> {
            if (CollectionUtils.isNotEmpty(gppSids)) {
                sb.append("&gpp_sid=").append(gppSids.stream().findFirst());
            }
        });
        Optional.ofNullable(optableAttributes.getTimeout()).ifPresent(timeout ->
                sb.append("&timeout=").append(timeout).append("ms"));
    }

    private void buildQueryString(StringBuilder sb, List<Id> ids) {
        final int size = ids.size();
        for (int index = 0; index < size; index++) {
            final Id id = ids.get(index);
            sb.append(URLEncoder.encode(
                    "%s:%s".formatted(id.getName(), id.getValue()),
                    StandardCharsets.UTF_8));
            if (index != size - 1) {
                sb.append("&id=");
            }
        }
    }
}
