package org.prebid.server.activity.infrastructure.creator.rule;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.creator.ActivityControllerCreationContext;
import org.prebid.server.activity.infrastructure.rule.ConditionsRule;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.settings.model.activity.rule.AccountActivityConditionsRuleConfig;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class ConditionsRuleCreator extends AbstractRuleCreator<AccountActivityConditionsRuleConfig> {

    public ConditionsRuleCreator() {
        super(AccountActivityConditionsRuleConfig.class);
    }

    @Override
    protected Rule fromConfiguration(AccountActivityConditionsRuleConfig ruleConfiguration,
                                     ActivityControllerCreationContext creationContext) {

        final boolean allow = allowFromConfig(ruleConfiguration.getAllow());
        final AccountActivityConditionsRuleConfig.Condition condition = ruleConfiguration.getCondition();

        return new ConditionsRule(
                condition != null ? setOf(condition.getComponentTypes()) : null,
                condition != null ? caseInsensitiveSetOf(condition.getComponentNames()) : null,
                sidsMatched(condition, creationContext.getGppContext().scope().getSectionsIds()),
                condition != null ? geoCodes(condition.getGeoCodes()) : null,
                condition != null ? condition.getGpc() : null,
                allow);
    }

    private static boolean allowFromConfig(Boolean configValue) {
        return configValue != null ? configValue : ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT;
    }

    private static Set<ComponentType> setOf(Collection<ComponentType> collection) {
        return collection != null ? new HashSet<>(collection) : null;
    }

    private static Set<String> caseInsensitiveSetOf(Collection<String> collection) {
        if (collection == null) {
            return null;
        }

        final Set<String> caseInsensitiveSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        caseInsensitiveSet.addAll(collection);
        return caseInsensitiveSet;
    }

    private static boolean sidsMatched(AccountActivityConditionsRuleConfig.Condition condition, Set<Integer> gppSids) {
        final List<Integer> sids = condition != null ? condition.getSids() : null;
        return sids == null || intersects(sids, gppSids);
    }

    private static boolean intersects(Collection<Integer> configurationSids, Collection<Integer> gppSids) {
        return CollectionUtils.isNotEmpty(configurationSids) && CollectionUtils.isNotEmpty(gppSids)
                && !CollectionUtils.intersection(configurationSids, gppSids).isEmpty();
    }

    private static List<ConditionsRule.GeoCode> geoCodes(List<String> stringGeoCodes) {
        return stringGeoCodes != null
                ? stringGeoCodes.stream()
                .map(ConditionsRuleCreator::from)
                .filter(Objects::nonNull)
                .toList()
                : null;
    }

    private static ConditionsRule.GeoCode from(String stringGeoCode) {
        if (StringUtils.isBlank(stringGeoCode)) {
            return null;
        }

        final int firstDot = stringGeoCode.indexOf(".");
        if (firstDot == -1) {
            return ConditionsRule.GeoCode.of(stringGeoCode, null);
        } else if (firstDot == stringGeoCode.length() - 1) {
            return ConditionsRule.GeoCode.of(stringGeoCode.substring(0, firstDot), null);
        }

        return ConditionsRule.GeoCode.of(
                stringGeoCode.substring(0, firstDot),
                stringGeoCode.substring(firstDot + 1));
    }
}
