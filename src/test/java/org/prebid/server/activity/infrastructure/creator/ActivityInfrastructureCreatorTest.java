package org.prebid.server.activity.infrastructure.creator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.ActivityController;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.debug.ActivityInfrastructureDebug;
import org.prebid.server.activity.infrastructure.rule.TestRule;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextCreator;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.Purpose;
import org.prebid.server.settings.model.PurposeEid;
import org.prebid.server.settings.model.Purposes;
import org.prebid.server.settings.model.activity.AccountActivityConfiguration;
import org.prebid.server.settings.model.activity.privacy.AccountUSCustomLogicModuleConfig;
import org.prebid.server.settings.model.activity.privacy.AccountUSNatModuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityConditionsRuleConfig;

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
import static org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier.US_NAT;

@ExtendWith(MockitoExtension.class)
public class ActivityInfrastructureCreatorTest {

    @Mock
    private ActivityRuleFactory activityRuleFactory;

    @Mock
    private Metrics metrics;

    @Mock
    private JacksonMapper jacksonMapper;

    @Mock
    private ActivityInfrastructureDebug debug;

    private ActivityInfrastructureCreator creator;

    @BeforeEach
    public void setUp() {
        creator = new ActivityInfrastructureCreator(activityRuleFactory, null, metrics, jacksonMapper);
    }

    @Test
    public void parseShouldReturnExpectedResultIfAccountPrivacyNull() {
        // given
        final Account account = Account.builder().build();

        // when
        final Map<Activity, ActivityController> controllers = creator.parse(account, null, debug);

        // then
        assertThat(controllers.keySet()).containsExactlyInAnyOrder(Activity.values());
    }

    @Test
    public void parseShouldReturnExpectedResultIfAccountPrivacyActivitiesNull() {
        // given
        final Account account = Account.builder().privacy(AccountPrivacyConfig.builder().build()).build();

        // when
        final Map<Activity, ActivityController> controllers = creator.parse(account, null, debug);

        // then
        assertThat(controllers.keySet()).containsExactlyInAnyOrder(Activity.values());
    }

    @Test
    public void parseShouldSkipPrivacyModulesDuplicatesAndEmitWarnings() {
        // given
        final Account account = Account.builder()
                .privacy(AccountPrivacyConfig.builder()
                        .activities(Map.of(Activity.SYNC_USER, AccountActivityConfiguration.of(
                                null, singletonList(AccountActivityConditionsRuleConfig.of(null, null)))))
                        .modules(asList(
                                AccountUSNatModuleConfig.of(null, 0, null),
                                AccountUSNatModuleConfig.of(null, 0, null)))
                        .build())
                .build();

        // when
        creator.parse(account, null, debug);

        // then
        verify(activityRuleFactory).from(any(), argThat(arg -> arg.getPrivacyModulesConfigs().size() == 1));
        verify(metrics).updateAlertsMetrics(eq(MetricName.general));
    }

    @Test
    public void parseShouldPopulateSkipConfigForModules() {
        // given
        final Account account = Account.builder()
                .privacy(AccountPrivacyConfig.builder()
                        .activities(Map.of(Activity.SYNC_USER, AccountActivityConfiguration.of(
                                null, singletonList(AccountActivityConditionsRuleConfig.of(null, null)))))
                        .modules(asList(
                                AccountUSNatModuleConfig.of(null, 100, null),
                                AccountUSCustomLogicModuleConfig.of(null, 0, null)))
                        .build())
                .build();

        // when
        creator.parse(account, null, debug);

        // then
        final ArgumentCaptor<ActivityControllerCreationContext> captor =
                ArgumentCaptor.forClass(ActivityControllerCreationContext.class);
        verify(activityRuleFactory).from(any(), captor.capture());
        assertThat(captor.getValue().getSkipPrivacyModules()).containsOnly(US_NAT);
    }

    @Test
    public void parseShouldReturnExpectedResult() {
        // given
        final Account account = Account.builder()
                .privacy(AccountPrivacyConfig.builder()
                        .activities(Map.of(
                                Activity.SYNC_USER, AccountActivityConfiguration.of(null, null),
                                Activity.CALL_BIDDER, AccountActivityConfiguration.of(false, null),
                                Activity.MODIFY_UFDP, AccountActivityConfiguration.of(true, null),
                                Activity.TRANSMIT_UFPD, AccountActivityConfiguration.of(true, singletonList(
                                        AccountActivityConditionsRuleConfig.of(null, null)))))
                        .build())
                .build();
        final GppContext gppContext = GppContextCreator.from(null, null).build().getGppContext();

        given(activityRuleFactory.from(
                same(account.getPrivacy().getActivities().get(Activity.TRANSMIT_UFPD).getRules().getFirst()),
                argThat(arg -> arg.getGppContext() == gppContext)))
                .willReturn(TestRule.disallowIfMatches(payload -> true));

        // when
        final Map<Activity, ActivityController> controllers = creator.parse(account, gppContext, debug);

        // then
        assertThat(controllers.keySet()).containsExactlyInAnyOrder(Activity.values());

        assertThat(controllers.get(Activity.SYNC_USER).isAllowed(null))
                .isEqualTo(ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT);
        assertThat(controllers.get(Activity.CALL_BIDDER).isAllowed(null)).isEqualTo(false);
        assertThat(controllers.get(Activity.MODIFY_UFDP).isAllowed(null)).isEqualTo(true);
        assertThat(controllers.get(Activity.TRANSMIT_UFPD).isAllowed(null)).isEqualTo(false);
    }

    @Test
    public void parseShouldReturnOriginalTransmitEidsActivity() {
        // given
        final Account account = Account.builder()
                .privacy(AccountPrivacyConfig.builder()
                        .activities(Map.of(Activity.TRANSMIT_UFPD, AccountActivityConfiguration.of(false, null)))
                        .build())
                .build();
        final GppContext gppContext = GppContextCreator.from(null, null).build().getGppContext();

        // when
        final Map<Activity, ActivityController> controllers = creator.parse(account, gppContext, debug);

        // then
        assertThat(controllers.keySet()).containsExactlyInAnyOrder(Activity.values());

        assertThat(controllers.get(Activity.CALL_BIDDER).isAllowed(null)).isEqualTo(true);
        assertThat(controllers.get(Activity.TRANSMIT_UFPD).isAllowed(null)).isEqualTo(false);
        assertThat(controllers.get(Activity.TRANSMIT_EIDS).isAllowed(null)).isEqualTo(true);
    }

    @Test
    public void parseShouldReturnImitatedTransmitEidsActivity() {
        // given
        final Account account = Account.builder()
                .privacy(AccountPrivacyConfig.builder()
                        .gdpr(AccountGdprConfig.builder()
                                .purposes(Purposes.builder()
                                        .p4(Purpose.of(null, null, null, PurposeEid.of(true, false, null)))
                                        .build())
                                .build())
                        .activities(Map.of(Activity.TRANSMIT_UFPD, AccountActivityConfiguration.of(false, null)))
                        .build())
                .build();
        final GppContext gppContext = GppContextCreator.from(null, null).build().getGppContext();

        // when
        final Map<Activity, ActivityController> controllers = creator.parse(account, gppContext, debug);

        // then
        assertThat(controllers.keySet()).containsExactlyInAnyOrder(Activity.values());

        assertThat(controllers.get(Activity.CALL_BIDDER).isAllowed(null)).isEqualTo(true);
        assertThat(controllers.get(Activity.TRANSMIT_UFPD).isAllowed(null)).isEqualTo(false);
        assertThat(controllers.get(Activity.TRANSMIT_EIDS).isAllowed(null)).isEqualTo(false);
    }
}
