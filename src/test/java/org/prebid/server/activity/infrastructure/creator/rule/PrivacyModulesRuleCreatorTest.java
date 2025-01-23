package org.prebid.server.activity.infrastructure.creator.rule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.activity.infrastructure.creator.ActivityControllerCreationContext;
import org.prebid.server.activity.infrastructure.creator.PrivacyModuleCreationContext;
import org.prebid.server.activity.infrastructure.creator.privacy.PrivacyModuleCreator;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier;
import org.prebid.server.activity.infrastructure.privacy.TestPrivacyModule;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.settings.model.activity.privacy.AccountPrivacyModuleConfig;
import org.prebid.server.settings.model.activity.privacy.AccountUSNatModuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityPrivacyModulesRuleConfig;

import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class PrivacyModulesRuleCreatorTest {

    @Mock
    private PrivacyModuleCreator privacyModuleCreator;

    private PrivacyModulesRuleCreator target;

    @BeforeEach
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
                PrivacyModuleQualifier.US_NAT, AccountUSNatModuleConfig.of(null, null, null)));

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
        final AccountPrivacyModuleConfig moduleConfig = AccountUSNatModuleConfig.of(null, null, null);
        final ActivityControllerCreationContext creationContext = creationContext(
                Map.of(PrivacyModuleQualifier.US_NAT, moduleConfig));

        given(privacyModuleCreator.from(eq(PrivacyModuleCreationContext.of(null, moduleConfig, null))))
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
        final AccountPrivacyModuleConfig moduleConfig = AccountUSNatModuleConfig.of(null, null, null);
        final ActivityControllerCreationContext creationContext = creationContext(
                Map.of(PrivacyModuleQualifier.US_NAT, moduleConfig));

        given(privacyModuleCreator.from(eq(PrivacyModuleCreationContext.of(null, moduleConfig, null))))
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
        final AccountPrivacyModuleConfig moduleConfig = AccountUSNatModuleConfig.of(null, null, null);
        final ActivityControllerCreationContext creationContext = creationContext(
                Map.of(PrivacyModuleQualifier.US_NAT, moduleConfig));

        given(privacyModuleCreator.from(eq(PrivacyModuleCreationContext.of(null, moduleConfig, null))))
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
                PrivacyModuleQualifier.US_NAT, AccountUSNatModuleConfig.of(true, null, null)));
        creationContext.use(PrivacyModuleQualifier.US_NAT);

        // when
        final Rule rule = target.from(config, creationContext);

        // then
        assertThat(rule.proceed(null)).isEqualTo(Rule.Result.ABSTAIN);
    }

    @Test
    public void fromShouldSkipDisabledPrivacyModule() {
        // given
        final AccountActivityPrivacyModulesRuleConfig config = AccountActivityPrivacyModulesRuleConfig.of(
                singletonList(PrivacyModuleQualifier.US_NAT.moduleName()));
        final ActivityControllerCreationContext creationContext = creationContext(Map.of(
                PrivacyModuleQualifier.US_NAT, AccountUSNatModuleConfig.of(false, null, null)));

        // when
        final Rule rule = target.from(config, creationContext);

        // then
        assertThat(rule.proceed(null)).isEqualTo(Rule.Result.ABSTAIN);
    }

    @Test
    public void fromShouldDisableSkippedPrivacyModule() {
        // given
        final AccountActivityPrivacyModulesRuleConfig config = AccountActivityPrivacyModulesRuleConfig.of(
                singletonList(PrivacyModuleQualifier.US_NAT.moduleName()));
        final ActivityControllerCreationContext creationContext = creationContext(Map.of(
                PrivacyModuleQualifier.US_NAT, AccountUSNatModuleConfig.of(true, 100, null)));

        // when
        final Rule rule = target.from(config, creationContext);

        // then
        assertThat(rule.proceed(null)).isEqualTo(Rule.Result.ABSTAIN);
    }

    @Test
    public void fromShouldSkipPrivacyModuleWithoutCreator() {
        // given
        target = new PrivacyModulesRuleCreator(emptyList());

        final AccountActivityPrivacyModulesRuleConfig config = AccountActivityPrivacyModulesRuleConfig.of(
                singletonList(PrivacyModuleQualifier.US_NAT.moduleName()));
        final ActivityControllerCreationContext creationContext = creationContext(Map.of(
                PrivacyModuleQualifier.US_NAT, AccountUSNatModuleConfig.of(null, null, null)));

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
