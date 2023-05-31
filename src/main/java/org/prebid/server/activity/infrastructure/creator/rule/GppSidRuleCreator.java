package org.prebid.server.activity.infrastructure.creator.rule;

import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.rule.GppSidRule;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.settings.model.activity.rule.AccountActivityGppSidRuleConfig;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class GppSidRuleCreator extends AbstractRuleCreator<AccountActivityGppSidRuleConfig> {

    public GppSidRuleCreator() {
        super(AccountActivityGppSidRuleConfig.class);
    }

    @Override
    protected Rule fromConfiguration(AccountActivityGppSidRuleConfig ruleConfiguration, GppContext gppContext) {
        final boolean allow = allowFromConfig(ruleConfiguration.getAllow());
        final AccountActivityGppSidRuleConfig.Condition condition = ruleConfiguration.getCondition();

        return new GppSidRule(
                condition != null ? setOf(condition.getComponentTypes()) : null,
                condition != null ? setOf(condition.getComponentNames()) : null,
                condition != null && intersects(condition.getSids(), gppContext.scope().getSectionsIds()),
                allow);
    }

    private static boolean allowFromConfig(Boolean configValue) {
        return configValue != null ? configValue : ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT;
    }

    private static <V> Set<V> setOf(Collection<V> collection) {
        return collection != null ? new HashSet<>(collection) : null;
    }

    private static boolean intersects(Collection<Integer> configurationSids, Collection<Integer> gppSids) {
        return CollectionUtils.isNotEmpty(configurationSids) && CollectionUtils.isNotEmpty(gppSids)
                && !CollectionUtils.intersection(configurationSids, gppSids).isEmpty();
    }
}
