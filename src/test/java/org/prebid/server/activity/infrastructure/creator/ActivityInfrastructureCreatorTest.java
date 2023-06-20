package org.prebid.server.activity.infrastructure.creator;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.ActivityCallResult;
import org.prebid.server.activity.infrastructure.ActivityConfiguration;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.rule.TestRule;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextCreator;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.activity.AccountActivityConfiguration;
import org.prebid.server.settings.model.activity.rule.AccountActivityComponentRuleConfig;

import java.util.Map;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;

public class ActivityInfrastructureCreatorTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ActivityRuleFactory activityRuleFactory;

    @Mock
    private Metrics metrics;

    private ActivityInfrastructureCreator creator;

    @Before
    public void setUp() {
        creator = new ActivityInfrastructureCreator(activityRuleFactory, metrics);
    }

    @Test
    public void parseShouldReturnExpectedResultIfAccountNull() {
        // when
        final Map<Activity, ActivityConfiguration> configuration = creator.parse(null, null);

        // then
        assertThat(configuration.keySet()).containsExactlyInAnyOrder(Activity.values());
    }

    @Test
    public void parseShouldReturnExpectedResultIfAccountPrivacyNull() {
        // given
        final Account account = Account.builder().build();

        // when
        final Map<Activity, ActivityConfiguration> configuration = creator.parse(account, null);

        // then
        assertThat(configuration.keySet()).containsExactlyInAnyOrder(Activity.values());
    }

    @Test
    public void parseShouldReturnExpectedResultIfAccountPrivacyActivitiesNull() {
        // given
        final Account account = Account.builder().privacy(AccountPrivacyConfig.of(null, null, null)).build();

        // when
        final Map<Activity, ActivityConfiguration> configuration = creator.parse(account, null);

        // then
        assertThat(configuration.keySet()).containsExactlyInAnyOrder(Activity.values());
    }

    @Test
    public void parseShouldReturnExpectedResult() {
        // given
        final Account account = Account.builder()
                .privacy(AccountPrivacyConfig.of(null, null, Map.of(
                        Activity.SYNC_USER, AccountActivityConfiguration.of(null, null),
                        Activity.CALL_BIDDER, AccountActivityConfiguration.of(false, null),
                        Activity.MODIFY_UFDP, AccountActivityConfiguration.of(true, null),
                        Activity.TRANSMIT_UFPD, AccountActivityConfiguration.of(true, singletonList(
                                AccountActivityComponentRuleConfig.of(null, null))))))
                .build();
        final GppContext gppContext = GppContextCreator.from(null, null).build().getGppContext();

        given(activityRuleFactory.from(
                same(account.getPrivacy().getActivities().get(Activity.TRANSMIT_UFPD).getRules().get(0)),
                same(gppContext)))
                .willReturn(TestRule.disallowIfMatches(payload -> true));

        // when
        final Map<Activity, ActivityConfiguration> configuration = creator.parse(account, gppContext);

        // then
        assertThat(configuration.keySet()).containsExactlyInAnyOrder(Activity.values());

        assertThat(configuration.get(Activity.SYNC_USER).isAllowed(null))
                .isEqualTo(ActivityCallResult.of(ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT, 0));

        assertThat(configuration.get(Activity.CALL_BIDDER).isAllowed(null))
                .isEqualTo(ActivityCallResult.of(false, 0));

        assertThat(configuration.get(Activity.MODIFY_UFDP).isAllowed(null))
                .isEqualTo(ActivityCallResult.of(true, 0));

        assertThat(configuration.get(Activity.TRANSMIT_UFPD).isAllowed(null))
                .isEqualTo(ActivityCallResult.of(false, 1));
    }
}
