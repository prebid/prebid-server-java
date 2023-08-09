package org.prebid.server.activity.infrastructure.creator.rule;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.creator.ActivityControllerCreationContext;
import org.prebid.server.activity.infrastructure.rule.GeoRule;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.settings.model.activity.rule.AccountActivityGeoRuleConfig;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class GeoRuleCreator extends AbstractRuleCreator<AccountActivityGeoRuleConfig> {

    public GeoRuleCreator() {
        super(AccountActivityGeoRuleConfig.class);
    }

    @Override
    protected Rule fromConfiguration(AccountActivityGeoRuleConfig ruleConfiguration,
                                     ActivityControllerCreationContext creationContext) {

        final boolean allow = allowFromConfig(ruleConfiguration.getAllow());
        final AccountActivityGeoRuleConfig.Condition condition = ruleConfiguration.getCondition();

        return new GeoRule(
                condition != null ? setOf(condition.getComponentTypes()) : null,
                condition != null ? setOf(condition.getComponentNames()) : null,
                sidsMatched(condition, creationContext.getGppContext().scope().getSectionsIds()),
                condition != null ? geoCodes(condition.getGeoCodes()) : null,
                condition != null ? condition.getGpc() : null,
                allow);
    }

    private static boolean allowFromConfig(Boolean configValue) {
        return configValue != null ? configValue : ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT;
    }

    private static <V> Set<V> setOf(Collection<V> collection) {
        return collection != null ? new HashSet<>(collection) : null;
    }

    private static boolean sidsMatched(AccountActivityGeoRuleConfig.Condition condition, Set<Integer> gppSids) {
        final List<Integer> sids = condition != null ? condition.getSids() : null;
        return sids == null || intersects(sids, gppSids);
    }

    private static boolean intersects(Collection<Integer> configurationSids, Collection<Integer> gppSids) {
        return CollectionUtils.isNotEmpty(configurationSids) && CollectionUtils.isNotEmpty(gppSids)
                && !CollectionUtils.intersection(configurationSids, gppSids).isEmpty();
    }

    private static List<GeoRule.GeoCode> geoCodes(List<String> stringGeoCodes) {
        return stringGeoCodes != null
                ? stringGeoCodes.stream()
                .map(GeoRuleCreator::from)
                .filter(Objects::nonNull)
                .toList()
                : null;
    }

    private static GeoRule.GeoCode from(String stringGeoCode) {
        if (StringUtils.isBlank(stringGeoCode)) {
            return null;
        }

        final int firstDot = stringGeoCode.indexOf(".");
        if (firstDot == -1) {
            return GeoRule.GeoCode.of(stringGeoCode, null);
        } else if (firstDot == stringGeoCode.length() - 1) {
            return GeoRule.GeoCode.of(stringGeoCode.substring(0, firstDot), null);
        }

        return GeoRule.GeoCode.of(
                stringGeoCode.substring(0, firstDot),
                stringGeoCode.substring(firstDot + 1));
    }
}
