package org.prebid.server.activity;

import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.activity.utils.AccountActivitiesConfigurationUtils;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountPrivacyConfig;

public class ActivitiesConfigResolver {

    private static final ConditionalLogger conditionalLogger =
            new ConditionalLogger(LoggerFactory.getLogger(ActivitiesConfigResolver.class));

    private final double logSamplingRate;

    public ActivitiesConfigResolver(double logSamplingRate) {
        this.logSamplingRate = logSamplingRate;
    }

    public Account resolve(Account account) {
        if (!AccountActivitiesConfigurationUtils.isInvalidActivitiesConfiguration(account)) {
            return account;
        }

        conditionalLogger.warn(
                "Activity configuration for account %s contains conditional rule with empty array."
                        .formatted(account.getId()),
                logSamplingRate);

        final AccountPrivacyConfig privacyConfig = account.getPrivacy();
        return account.toBuilder()
                .privacy(AccountPrivacyConfig.of(
                        privacyConfig.getGdpr(),
                        privacyConfig.getCcpa(),
                        privacyConfig.getDsa(),
                        AccountActivitiesConfigurationUtils.removeInvalidRules(privacyConfig.getActivities()),
                        privacyConfig.getModules()))
                .build();

    }

}
