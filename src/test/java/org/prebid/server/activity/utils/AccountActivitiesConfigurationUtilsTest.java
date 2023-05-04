package org.prebid.server.activity.utils;

import org.junit.Test;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ActivityConfiguration;
import org.prebid.server.activity.ActivityContextResult;
import org.prebid.server.activity.ActivityInfrastructure;
import org.prebid.server.activity.ActivityPayload;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountConsentConfig;
import org.prebid.server.settings.model.activity.AccountActivityConfiguration;
import org.prebid.server.settings.model.activity.rule.AccountActivityComponentRuleConfig;

import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class AccountActivitiesConfigurationUtilsTest {

    @Test
    public void parseShouldReturnExpectedResultIfAccountNull() {
        // when
        final Map<Activity, ActivityConfiguration> configuration = AccountActivitiesConfigurationUtils.parse(null);

        // then
        assertThat(configuration.keySet()).containsExactlyInAnyOrder(Activity.values());
    }

    @Test
    public void parseShouldReturnExpectedResultIfAccountPrivacyNull() {
        // given
        final Account account = Account.builder().build();

        // when
        final Map<Activity, ActivityConfiguration> configuration = AccountActivitiesConfigurationUtils.parse(account);

        // then
        assertThat(configuration.keySet()).containsExactlyInAnyOrder(Activity.values());
    }

    @Test
    public void parseShouldReturnExpectedResultIfAccountConsentActivitiesNull() {
        // given
        final Account account = Account.builder().consent(AccountConsentConfig.of(null)).build();

        // when
        final Map<Activity, ActivityConfiguration> configuration = AccountActivitiesConfigurationUtils.parse(account);

        // then
        assertThat(configuration.keySet()).containsExactlyInAnyOrder(Activity.values());
    }

    @Test
    public void parseShouldReturnExpectedResult() {
        // given
        final Account account = Account.builder()
                .consent(AccountConsentConfig.of(Map.of(
                        Activity.SYNC_USER, AccountActivityConfiguration.of(null, null),
                        Activity.CALL_BIDDER, AccountActivityConfiguration.of(
                                !ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT,
                                singletonList(AccountActivityComponentRuleConfig.of(null, null))),
                        Activity.MODIFY_UFDP, AccountActivityConfiguration.of(true, singletonList(
                                AccountActivityComponentRuleConfig.of(
                                        AccountActivityComponentRuleConfig.Condition.of(null, null),
                                        false))),
                        Activity.TRANSMIT_UFPD, AccountActivityConfiguration.of(true, singletonList(
                                AccountActivityComponentRuleConfig.of(
                                        AccountActivityComponentRuleConfig.Condition.of(
                                                singletonList(ComponentType.BIDDER),
                                                singletonList("bidder")),
                                        false))))))
                .build();

        // when
        final Map<Activity, ActivityConfiguration> configuration = AccountActivitiesConfigurationUtils.parse(account);

        // then
        assertThat(configuration.keySet()).containsExactlyInAnyOrder(Activity.values());

        assertThat(configuration.get(Activity.SYNC_USER).isAllowed(null))
                .isEqualTo(ActivityContextResult.of(ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT, 0));

        assertThat(configuration.get(Activity.CALL_BIDDER).isAllowed(null))
                .isEqualTo(ActivityContextResult.of(ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT, 1));

        assertThat(configuration.get(Activity.MODIFY_UFDP).isAllowed(null))
                .isEqualTo(ActivityContextResult.of(false, 1));

        assertThat(configuration.get(Activity.TRANSMIT_UFPD)).satisfies(activityConfiguration -> {
            assertThat(activityConfiguration.isAllowed(ActivityPayload.of(ComponentType.BIDDER, "bidder")))
                    .isEqualTo(ActivityContextResult.of(false, 1));
            assertThat(activityConfiguration.isAllowed(ActivityPayload.of(ComponentType.BIDDER, null)))
                    .isEqualTo(ActivityContextResult.of(true, 1));
        });
    }

    @Test
    public void isInvalidActivitiesConfigurationShouldReturnFalseIfAccountNull() {
        // when
        final boolean result = AccountActivitiesConfigurationUtils.isInvalidActivitiesConfiguration(null);

        // then
        assertThat(result).isEqualTo(false);
    }

    @Test
    public void isInvalidActivitiesConfigurationShouldReturnFalseIfAccountPrivacyNull() {
        //given
        final Account account = Account.builder().build();

        // when
        final boolean result = AccountActivitiesConfigurationUtils.isInvalidActivitiesConfiguration(account);

        // then
        assertThat(result).isEqualTo(false);
    }

    @Test
    public void isInvalidActivitiesConfigurationShouldReturnFalseIfAccountPrivacyActivitiesNull() {
        //given
        final Account account = Account.builder().consent(AccountConsentConfig.of(null)).build();

        // when
        final boolean result = AccountActivitiesConfigurationUtils.isInvalidActivitiesConfiguration(account);

        // then
        assertThat(result).isEqualTo(false);
    }

    @Test
    public void isInvalidActivitiesConfigurationShouldReturnFalseIfConfigurationValid() {
        //given
        final Account account = Account.builder()
                .consent(AccountConsentConfig.of(Map.of(
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
                                        null))))))
                .build();

        // when
        final boolean result = AccountActivitiesConfigurationUtils.isInvalidActivitiesConfiguration(account);

        // then
        assertThat(result).isEqualTo(false);
    }

    @Test
    public void isInvalidActivitiesConfigurationShouldReturnTrueOnInvalidConditionalRule() {
        //given
        final Account account = Account.builder()
                .consent(AccountConsentConfig.of(Map.of(
                        Activity.CALL_BIDDER, AccountActivityConfiguration.of(null, singletonList(
                                AccountActivityComponentRuleConfig.of(
                                        AccountActivityComponentRuleConfig.Condition.of(emptyList(), emptyList()),
                                        null))))))
                .build();

        // when
        final boolean result = AccountActivitiesConfigurationUtils.isInvalidActivitiesConfiguration(account);

        // then
        assertThat(result).isEqualTo(true);
    }

    @Test
    public void removeInvalidRulesShouldReturnExpectedResult() {
        //given
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
                                null)))));
    }
}
