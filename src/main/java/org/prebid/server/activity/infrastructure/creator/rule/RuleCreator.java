package org.prebid.server.activity.infrastructure.creator.rule;

import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.auction.gpp.model.GppContext;

public interface RuleCreator<T> {

    Class<T> relatedConfigurationClass();

    Rule from(Object ruleConfiguration, GppContext gppContext);
}
