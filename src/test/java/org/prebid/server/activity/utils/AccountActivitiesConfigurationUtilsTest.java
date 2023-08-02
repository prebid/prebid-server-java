package org.prebid.server.activity.utils;

import org.junit.Test;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
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

public class AccountActivitiesConfigurationUtilsTest {

    @Test
    public void isInvalidActivitiesConfigurationShouldReturnFalseIfAccountNull() {
        // when
        final boolean result = AccountActivitiesConfigurationUtils.isInvalidActivitiesConfiguration(null);

        // then
        assertThat(result).isEqualTo(false);
    }

    @Test
    public void isInvalidActivitiesConfigurationShouldReturnFalseIfAccountPrivacyNull() {
        // given
        final Account account = Account.builder().build();

        // when
        final boolean result = AccountActivitiesConfigurationUtils.isInvalidActivitiesConfiguration(account);

        // then
        assertThat(result).isEqualTo(false);
    }

    @Test
    public void isInvalidActivitiesConfigurationShouldReturnFalseIfAccountPrivacyActivitiesNull() {
        // given
        final Account account = Account.builder().privacy(AccountPrivacyConfig.of(null, null, null, null)).build();

        // when
        final boolean result = AccountActivitiesConfigurationUtils.isInvalidActivitiesConfiguration(account);

        // then
        assertThat(result).isEqualTo(false);
    }

    @Test
    public void isInvalidActivitiesConfigurationShouldReturnFalseIfConfigurationValid() {
        // given
        final Account account = Account.builder()
                .privacy(AccountPrivacyConfig.of(
                        null,
                        null,
                        Map.of(
                                Activity.SYNC_USER, AccountActivityConfiguration.of(null, null),
                                Activity.CALL_BIDDER, AccountActivityConfiguration.of(null, asList(
                                        null,
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
                                                null)))),
                        null))
                .build();

        // when
        final boolean result = AccountActivitiesConfigurationUtils.isInvalidActivitiesConfiguration(account);

        // then
        assertThat(result).isEqualTo(false);
    }

    @Test
    public void isInvalidActivitiesConfigurationShouldReturnTrueOnInvalidComponentRule() {
        // given
        final Account account = Account.builder()
                .privacy(AccountPrivacyConfig.of(
                        null,
                        null,
                        Map.of(Activity.CALL_BIDDER, AccountActivityConfiguration.of(null, singletonList(
                                AccountActivityComponentRuleConfig.of(
                                        AccountActivityComponentRuleConfig.Condition.of(emptyList(), emptyList()),
                                        null)))),
                        null))
                .build();

        // when
        final boolean result = AccountActivitiesConfigurationUtils.isInvalidActivitiesConfiguration(account);

        // then
        assertThat(result).isEqualTo(true);
    }

    @Test
    public void isInvalidActivitiesConfigurationShouldReturnTrueOnInvalidGeoRule() {
        // given
        final Account account = Account.builder()
                .privacy(AccountPrivacyConfig.of(
                        null,
                        null,
                        Map.of(Activity.CALL_BIDDER, AccountActivityConfiguration.of(null, singletonList(
                                AccountActivityGeoRuleConfig.of(
                                        AccountActivityGeoRuleConfig.Condition.of(
                                                emptyList(), emptyList(), null, null, null),
                                        null)))),
                        null))
                .build();

        // when
        final boolean result = AccountActivitiesConfigurationUtils.isInvalidActivitiesConfiguration(account);

        // then
        assertThat(result).isEqualTo(true);
    }

    @Test
    public void removeInvalidRulesShouldReturnExpectedResult() {
        // given
        final Map<Activity, AccountActivityConfiguration> configuration = Map.of(
                Activity.SYNC_USER, AccountActivityConfiguration.of(null, null),
                Activity.CALL_BIDDER, AccountActivityConfiguration.of(null, asList(
                        AccountActivityComponentRuleConfig.of(null, null),
                        AccountActivityComponentRuleConfig.of(
                                AccountActivityComponentRuleConfig.Condition.of(null, null),
                                null),
                        AccountActivityComponentRuleConfig.of(
                                AccountActivityComponentRuleConfig.Condition.of(emptyList(), emptyList()),
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
                                        singletonList(ComponentType.BIDDER), singletonList("bidder"), null, null, null),
                                null))));

        // when
        final Map<Activity, AccountActivityConfiguration> result = AccountActivitiesConfigurationUtils
                .removeInvalidRules(configuration);

        // then
        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(
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
                                        singletonList(ComponentType.BIDDER), singletonList("bidder"), null, null, null),
                                null)))));
    }
}
