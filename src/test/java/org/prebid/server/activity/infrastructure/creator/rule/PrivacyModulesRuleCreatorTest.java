package org.prebid.server.activity.infrastructure.creator.rule;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.activity.infrastructure.creator.ActivityControllerCreationContext;
import org.prebid.server.activity.infrastructure.creator.privacy.PrivacyModuleCreator;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier;
import org.prebid.server.activity.infrastructure.privacy.TestPrivacyModule;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.settings.model.activity.privacy.AccountPrivacyModuleConfig;
import org.prebid.server.settings.model.activity.privacy.AccountUSNatModuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityPrivacyModulesRuleConfig;

import java.util.Map;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;

public class PrivacyModulesRuleCreatorTest {

    @org.junit.Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private PrivacyModuleCreator privacyModuleCreator;

    private PrivacyModulesRuleCreator target;

    @Before
    public void setUp() {
        given(privacyModuleCreator.qualifier()).willReturn(PrivacyModuleQualifier.US_NAT);

        target = new PrivacyModulesRuleCreator(singletonList(privacyModuleCreator));
    }

    @Test
    public void fromShouldCreateDefaultRule() {
        // given
        final AccountActivityPrivacyModulesRuleConfig config = AccountActivityPrivacyModulesRuleConfig.of(null);

        // when
        final Rule rule = target.from(config, creationContext(null));

        // then
        assertThat(rule.proceed(null)).isEqualTo(Rule.Result.ABSTAIN);
    }

    @Test
    public void fromShouldCreateDefaultRuleIfNoneOfConfiguredPrivacyModulesMatches() {
        // given
        final AccountActivityPrivacyModulesRuleConfig config = AccountActivityPrivacyModulesRuleConfig.of(
                singletonList("not_configured"));
        final ActivityControllerCreationContext creationContext = creationContext(Map.of(
                PrivacyModuleQualifier.US_NAT, AccountUSNatModuleConfig.of(null)));

        // when
        final Rule rule = target.from(config, creationContext);

        // then
        assertThat(rule.proceed(null)).isEqualTo(Rule.Result.ABSTAIN);
    }

    @Test
    public void fromShouldCreateRuleWithAllConfiguredPrivacyModules() {
        // given
        final AccountActivityPrivacyModulesRuleConfig config = AccountActivityPrivacyModulesRuleConfig.of(
                singletonList("*"));
        final ActivityControllerCreationContext creationContext = creationContext(Map.of(
                PrivacyModuleQualifier.US_NAT, AccountUSNatModuleConfig.of(null)));

        given(privacyModuleCreator.from(same(creationContext)))
                .willReturn(TestPrivacyModule.of(Rule.Result.ALLOW));

        // when
        final Rule rule = target.from(config, creationContext);

        // then
        assertThat(rule.proceed(null)).isEqualTo(Rule.Result.ALLOW);
    }

    @Test
    public void fromShouldCreateRuleWithAllConfiguredPrivacyModulesThatMatches() {
        // given
        final AccountActivityPrivacyModulesRuleConfig config = AccountActivityPrivacyModulesRuleConfig.of(
                singletonList("iab.*"));
        final ActivityControllerCreationContext creationContext = creationContext(Map.of(
                PrivacyModuleQualifier.US_NAT, AccountUSNatModuleConfig.of(null)));

        given(privacyModuleCreator.from(same(creationContext)))
                .willReturn(TestPrivacyModule.of(Rule.Result.ALLOW));

        // when
        final Rule rule = target.from(config, creationContext);

        // then
        assertThat(rule.proceed(null)).isEqualTo(Rule.Result.ALLOW);
    }

    @Test
    public void fromShouldCreateRuleAndModifyContextWithUsedPrivacyModules() {
        // given
        final AccountActivityPrivacyModulesRuleConfig config = AccountActivityPrivacyModulesRuleConfig.of(
                singletonList(PrivacyModuleQualifier.US_NAT.moduleName()));
        final ActivityControllerCreationContext creationContext = creationContext(Map.of(
                PrivacyModuleQualifier.US_NAT, AccountUSNatModuleConfig.of(null)));

        given(privacyModuleCreator.from(same(creationContext)))
                .willReturn(TestPrivacyModule.of(Rule.Result.ALLOW));

        // when
        final Rule rule = target.from(config, creationContext);

        // then
        assertThat(rule.proceed(null)).isEqualTo(Rule.Result.ALLOW);
        assertThat(creationContext.isUsed(PrivacyModuleQualifier.US_NAT)).isTrue();
    }

    @Test
    public void fromShouldSkipAlreadyUsedPrivacyModule() {
        // given
        final AccountActivityPrivacyModulesRuleConfig config = AccountActivityPrivacyModulesRuleConfig.of(
                singletonList(PrivacyModuleQualifier.US_NAT.moduleName()));
        final ActivityControllerCreationContext creationContext = creationContext(Map.of(
                PrivacyModuleQualifier.US_NAT, AccountUSNatModuleConfig.of(null)));
        creationContext.use(PrivacyModuleQualifier.US_NAT);

        // when
        final Rule rule = target.from(config, creationContext);

        // then
        assertThat(rule.proceed(null)).isEqualTo(Rule.Result.ABSTAIN);
    }

    private static ActivityControllerCreationContext creationContext(
            Map<PrivacyModuleQualifier, AccountPrivacyModuleConfig> modulesConfigs) {

        return ActivityControllerCreationContext.of(null, modulesConfigs, null);
    }
}
