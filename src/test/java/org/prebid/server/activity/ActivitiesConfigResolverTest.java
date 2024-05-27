package org.prebid.server.activity;

import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.activity.AccountActivityConfiguration;
import org.prebid.server.settings.model.activity.rule.AccountActivityComponentRuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityGeoRuleConfig;

import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class ActivitiesConfigResolverTest extends VertxTest {

    private final ActivitiesConfigResolver target = new ActivitiesConfigResolver(0);

    @Test
    public void resolveShouldReturnNullWhenAccountNull() {
        // when
        final Account result = target.resolve(null);

        // then
        assertThat(result).isEqualTo(null);
    }

    @Test
    public void resolveShouldReturnAccountAsIsWhenAccountPrivacyNull() {
        // given
        final Account givenAccount = Account.builder().build();

        // when
        final Account result = target.resolve(givenAccount);

        // then
        assertThat(result).isEqualTo(givenAccount);
    }

    @Test
    public void resolveShouldReturnAccountAsIsWhenAccountPrivacyActivitiesNull() {
        // given
        final Account givenAccount = Account.builder().privacy(AccountPrivacyConfig.builder().build()).build();

        // when
        final Account result = target.resolve(givenAccount);

        // then
        assertThat(result).isEqualTo(givenAccount);
    }

    @Test
    public void resolveShouldRemoveInvalidRulesFromAccountActivitiesConfiguration() {
        // given
        final Account givenAccount = Account.builder()
                .privacy(AccountPrivacyConfig.builder().activities(
                        Map.of(
                                Activity.SYNC_USER, AccountActivityConfiguration.of(null, null),
                                Activity.CALL_BIDDER, AccountActivityConfiguration.of(null, asList(
                                        AccountActivityComponentRuleConfig.of(null, null),
                                        AccountActivityComponentRuleConfig.of(
                                                AccountActivityComponentRuleConfig.Condition.of(null, null),
                                                null),
                                        AccountActivityComponentRuleConfig.of(
                                                AccountActivityComponentRuleConfig.Condition.of(
                                                        emptyList(),
                                                        emptyList()),
                                                null),
                                        AccountActivityComponentRuleConfig.of(
                                                AccountActivityComponentRuleConfig.Condition.of(
                                                        singletonList(ComponentType.BIDDER), singletonList("bidder")),
                                                null))),
                                Activity.MODIFY_UFDP, AccountActivityConfiguration.of(null, asList(
                                        AccountActivityGeoRuleConfig.of(null, null),
                                        AccountActivityGeoRuleConfig.of(
                                                AccountActivityGeoRuleConfig.Condition.of(null, null, null, null, null),
                                                null),
                                        AccountActivityGeoRuleConfig.of(
                                                AccountActivityGeoRuleConfig.Condition.of(
                                                        emptyList(), emptyList(), null, null, null),
                                                null),
                                        AccountActivityGeoRuleConfig.of(
                                                AccountActivityGeoRuleConfig.Condition.of(
                                                        singletonList(ComponentType.BIDDER),
                                                        singletonList("bidder"),
                                                        null,
                                                        null,
                                                        null),
                                                null)))))
                        .build())
                .build();

        // when
        final Account actualAccount = target.resolve(givenAccount);

        // then
        assertThat(actualAccount).isEqualTo(Account.builder()
                .privacy(AccountPrivacyConfig.builder().activities(
                        Map.of(
                                Activity.SYNC_USER, AccountActivityConfiguration.of(null, null),
                                Activity.CALL_BIDDER, AccountActivityConfiguration.of(null, asList(
                                        AccountActivityComponentRuleConfig.of(null, null),
                                        AccountActivityComponentRuleConfig.of(
                                                AccountActivityComponentRuleConfig.Condition.of(null, null),
                                                null),
                                        AccountActivityComponentRuleConfig.of(
                                                AccountActivityComponentRuleConfig.Condition.of(
                                                        singletonList(ComponentType.BIDDER), singletonList("bidder")),
                                                null))),
                                Activity.MODIFY_UFDP, AccountActivityConfiguration.of(null, asList(
                                        AccountActivityGeoRuleConfig.of(null, null),
                                        AccountActivityGeoRuleConfig.of(
                                                AccountActivityGeoRuleConfig.Condition.of(null, null, null, null, null),
                                                null),
                                        AccountActivityGeoRuleConfig.of(
                                                AccountActivityGeoRuleConfig.Condition.of(
                                                        singletonList(ComponentType.BIDDER),
                                                        singletonList("bidder"),
                                                        null,
                                                        null,
                                                        null),
                                                null))))).build())
                .build());
    }
}
