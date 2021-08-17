package org.prebid.server.spring.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAnalyticsConfig;
import org.prebid.server.settings.model.AccountBidValidationConfig;
import org.prebid.server.settings.model.AccountCcpaConfig;
import org.prebid.server.settings.model.AccountCookieSyncConfig;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountStatus;

@Data
@NoArgsConstructor
public class AccountConfigurationProperties {

    private String priceGranularity;

    private Integer bannerCacheTtl;

    private Integer videoCacheTtl;

    private Boolean eventsEnabled;

    private Boolean enforceCcpa;

    private String gdpr;

    private String ccpa;

    private Integer analyticsSamplingFactor;

    private Integer truncateTargetAttr;

    private String defaultIntegration;

    private String analyticsConfig;

    private String bidValidations;

    private AccountStatus status;

    private String cookieSync;

    public Account toAccount(JacksonMapper mapper) {
        return Account.builder()
                .priceGranularity(getPriceGranularity())
                .bannerCacheTtl(getBannerCacheTtl())
                .videoCacheTtl(getVideoCacheTtl())
                .eventsEnabled(getEventsEnabled())
                .enforceCcpa(getEnforceCcpa())
                .gdpr(toModel(mapper, getGdpr(), AccountGdprConfig.class))
                .ccpa(toModel(mapper, getCcpa(), AccountCcpaConfig.class))
                .analyticsSamplingFactor(getAnalyticsSamplingFactor())
                .truncateTargetAttr(getTruncateTargetAttr())
                .defaultIntegration(getDefaultIntegration())
                .analyticsConfig(toModel(mapper, getAnalyticsConfig(), AccountAnalyticsConfig.class))
                .bidValidations(toModel(mapper, getBidValidations(), AccountBidValidationConfig.class))
                .status(getStatus())
                .cookieSync(toModel(mapper, getCookieSync(), AccountCookieSyncConfig.class))
                .build();
    }

    private static <T> T toModel(JacksonMapper mapper, String source, Class<T> targetClass) {
        return source != null ? mapper.decodeValue(source, targetClass) : null;
    }
}
