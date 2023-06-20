package org.prebid.server.activity.infrastructure.creator.rule;

import org.junit.Test;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityCallPayloadImpl;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.settings.model.activity.rule.AccountActivityComponentRuleConfig;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class ComponentRuleCreatorTest {

    private final ComponentRuleCreator target = new ComponentRuleCreator();

    @Test
    public void fromShouldCreateDefaultRule() {
        // given
        final AccountActivityComponentRuleConfig config = AccountActivityComponentRuleConfig.of(null, null);

        // when
        final Rule rule = target.from(config, null);

        // then
        assertThat(rule.matches(null)).isTrue();
        assertThat(rule.allowed()).isEqualTo(ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT);
    }

    @Test
    public void fromShouldCreateExpectedRule() {
        // given
        final AccountActivityComponentRuleConfig config = AccountActivityComponentRuleConfig.of(
                AccountActivityComponentRuleConfig.Condition.of(
                        singletonList(ComponentType.BIDDER),
                        singletonList("name")),
                false);

        // when
        final Rule rule = target.from(config, null);

        // then
        assertThat(rule.matches(ActivityCallPayloadImpl.of(ComponentType.BIDDER, "name"))).isTrue();
        assertThat(rule.allowed()).isFalse();
    }
}
