package org.prebid.server.activity.infrastructure.creator;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.ActivityCallResult;
import org.prebid.server.activity.infrastructure.ActivityController;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.rule.TestRule;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextCreator;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.activity.AccountActivityConfiguration;
import org.prebid.server.settings.model.activity.privacy.AccountUSNatModuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityComponentRuleConfig;

import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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
    public void parseShouldReturnExpectedResultIfAccountPrivacyNull() {
        // given
        final Account account = Account.builder().build();

        // when
        final Map<Activity, ActivityController> controllers = creator.parse(account, null);

        // then
        assertThat(controllers.keySet()).containsExactlyInAnyOrder(Activity.values());
    }

    @Test
    public void parseShouldReturnExpectedResultIfAccountPrivacyActivitiesNull() {
        // given
        final Account account = Account.builder().privacy(AccountPrivacyConfig.of(null, null, null, null)).build();

        // when
        final Map<Activity, ActivityController> controllers = creator.parse(account, null);

        // then
        assertThat(controllers.keySet()).containsExactlyInAnyOrder(Activity.values());
    }

    @Test
    public void parseShouldSkipPrivacyModulesDuplicatesAndEmitWarnings() {
        // given
        final Account account = Account.builder()
                .privacy(AccountPrivacyConfig.of(
                        null,
                        null,
                        Map.of(Activity.SYNC_USER, AccountActivityConfiguration.of(
                                null, singletonList(AccountActivityComponentRuleConfig.of(null, null)))),
                        asList(
                                AccountUSNatModuleConfig.of(null, null),
                                AccountUSNatModuleConfig.of(null, null))))
                .build();

        // when
        creator.parse(account, null);

        // then
        verify(activityRuleFactory).from(
                any(),
                argThat(creationContext -> creationContext.getPrivacyModulesConfigs().size() == 1));
        verify(metrics).updateAlertsMetrics(eq(MetricName.general));
    }

    @Test
    public void parseShouldReturnExpectedResult() {
        // given
        final Account account = Account.builder()
                .privacy(AccountPrivacyConfig.of(
                        null,
                        null,
                        Map.of(
                                Activity.SYNC_USER, AccountActivityConfiguration.of(null, null),
                                Activity.CALL_BIDDER, AccountActivityConfiguration.of(false, null),
                                Activity.MODIFY_UFDP, AccountActivityConfiguration.of(true, null),
                                Activity.TRANSMIT_UFPD, AccountActivityConfiguration.of(true, singletonList(
                                        AccountActivityComponentRuleConfig.of(null, null)))),
                        null))
                .build();
        final GppContext gppContext = GppContextCreator.from(null, null).build().getGppContext();

        given(activityRuleFactory.from(
                same(account.getPrivacy().getActivities().get(Activity.TRANSMIT_UFPD).getRules().get(0)),
                argThat(arg -> arg.getGppContext() == gppContext)))
                .willReturn(TestRule.disallowIfMatches(payload -> true));

        // when
        final Map<Activity, ActivityController> controllers = creator.parse(account, gppContext);

        // then
        assertThat(controllers.keySet()).containsExactlyInAnyOrder(Activity.values());

        assertThat(controllers.get(Activity.SYNC_USER).isAllowed(null))
                .isEqualTo(ActivityCallResult.of(ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT, 0));

        assertThat(controllers.get(Activity.CALL_BIDDER).isAllowed(null))
                .isEqualTo(ActivityCallResult.of(false, 0));

        assertThat(controllers.get(Activity.MODIFY_UFDP).isAllowed(null))
                .isEqualTo(ActivityCallResult.of(true, 0));

        assertThat(controllers.get(Activity.TRANSMIT_UFPD).isAllowed(null))
                .isEqualTo(ActivityCallResult.of(false, 1));
    }
}
