package org.prebid.server.activity.infrastructure.creator.rule;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.activity.infrastructure.creator.ActivityControllerCreationContext;
import org.prebid.server.activity.infrastructure.rule.GeoRule;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.settings.model.activity.rule.AccountActivityConditionsRuleConfig;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class GeoRuleCreator extends AbstractRuleCreator<AccountActivityConditionsRuleConfig> {

    public GeoRuleCreator() {
        super(AccountActivityConditionsRuleConfig.class);
    }

    @Override
    protected Rule fromConfiguration(AccountActivityConditionsRuleConfig ruleConfiguration,
                                     ActivityControllerCreationContext creationContext) {

        final AccountActivityConditionsRuleConfig.Condition condition = ruleConfiguration.getCondition();

        return new GeoRule(
                sidsMatched(condition.getSids(), creationContext.getGppContext().scope().getSectionsIds()),
                geoCodes(condition.getGeoCodes()),
                allowFromConfig(ruleConfiguration.getAllow()));
    }

    private static boolean sidsMatched(List<Integer> sids, Set<Integer> gppSids) {
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
