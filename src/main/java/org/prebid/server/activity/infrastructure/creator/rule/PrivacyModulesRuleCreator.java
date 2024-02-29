package org.prebid.server.activity.infrastructure.creator.rule;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.activity.infrastructure.creator.ActivityControllerCreationContext;
import org.prebid.server.activity.infrastructure.creator.PrivacyModuleCreationContext;
import org.prebid.server.activity.infrastructure.creator.privacy.PrivacyModuleCreator;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier;
import org.prebid.server.activity.infrastructure.rule.AndRule;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.settings.model.activity.privacy.AccountPrivacyModuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityPrivacyModulesRuleConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class PrivacyModulesRuleCreator extends AbstractRuleCreator<AccountActivityPrivacyModulesRuleConfig> {

    private static final String WILDCARD = "*";

    private final Map<PrivacyModuleQualifier, PrivacyModuleCreator> privacyModulesCreators;

    public PrivacyModulesRuleCreator(List<PrivacyModuleCreator> privacyModulesCreators) {
        super(AccountActivityPrivacyModulesRuleConfig.class);

        this.privacyModulesCreators = Objects.requireNonNull(privacyModulesCreators).stream()
                .collect(Collectors.toMap(PrivacyModuleCreator::qualifier, UnaryOperator.identity()));
    }

    @Override
    protected Rule fromConfiguration(AccountActivityPrivacyModulesRuleConfig ruleConfiguration,
                                     ActivityControllerCreationContext creationContext) {

        final List<String> configuredModulesNames = ruleConfiguration.getPrivacyModules();

        final List<PrivacyModule> privacyModules = ListUtils.emptyIfNull(configuredModulesNames).stream()
                .map(configuredModuleName -> mapToModulesQualifiers(configuredModuleName, creationContext))
                .flatMap(Collection::stream)
                .filter(qualifier -> !creationContext.isUsed(qualifier))
                .peek(creationContext::use)
                .map(qualifier -> createPrivacyModule(qualifier, creationContext))
                .toList();

        return new AndRule(privacyModules);
    }

    private List<PrivacyModuleQualifier> mapToModulesQualifiers(
            String configuredModuleName,
            ActivityControllerCreationContext creationContext) {

        if (StringUtils.isBlank(configuredModuleName)) {
            return Collections.emptyList();
        }

        final String moduleNamePattern = eraseWildcard(configuredModuleName);
        return creationContext.getPrivacyModulesConfigs().entrySet().stream()
                .filter(entry -> isModuleEnabled(entry.getValue()))
                .map(Map.Entry::getKey)
                .filter(qualifier -> qualifier.moduleName().startsWith(moduleNamePattern))
                .filter(privacyModulesCreators::containsKey)
                .toList();
    }

    private static String eraseWildcard(String configuredModuleName) {
        final int wildcardIndex = configuredModuleName.indexOf(WILDCARD);
        return wildcardIndex != -1
                ? configuredModuleName.substring(0, wildcardIndex)
                : configuredModuleName;
    }

    private static boolean isModuleEnabled(AccountPrivacyModuleConfig accountPrivacyModuleConfig) {
        final Boolean enabled = accountPrivacyModuleConfig.enabled();
        return enabled == null || enabled;
    }

    private PrivacyModule createPrivacyModule(PrivacyModuleQualifier privacyModuleQualifier,
                                              ActivityControllerCreationContext creationContext) {

        return privacyModulesCreators.get(privacyModuleQualifier)
                .from(creationContext(privacyModuleQualifier, creationContext));
    }

    private static PrivacyModuleCreationContext creationContext(PrivacyModuleQualifier privacyModuleQualifier,
                                                                ActivityControllerCreationContext creationContext) {

        return PrivacyModuleCreationContext.of(
                creationContext.getActivity(),
                creationContext.getPrivacyModulesConfigs().get(privacyModuleQualifier),
                creationContext.getGppContext());
    }
}
