package org.prebid.server.activity.infrastructure.creator.rule;

import org.prebid.server.activity.infrastructure.creator.ActivityControllerCreationContext;
import org.prebid.server.activity.infrastructure.rule.Rule;

public interface RuleCreator<T> {

    Class<T> relatedConfigurationClass();

    Rule from(Object ruleConfiguration, ActivityControllerCreationContext activityControllerCreationContext);
}
