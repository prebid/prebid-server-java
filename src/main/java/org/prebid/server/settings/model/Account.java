package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.ObjectUtils;

@Builder(toBuilder = true)
@Value
public class Account {

    String id;

    String priceGranularity;

    Integer bannerCacheTtl;

    Integer videoCacheTtl;

    Boolean eventsEnabled;

    Boolean enforceCcpa;

    AccountGdprConfig gdpr;

    Integer analyticsSamplingFactor;

    Integer truncateTargetAttr;

    String defaultIntegration;

    AccountAnalyticsConfig analyticsConfig;

    AccountBidValidationConfig bidValidations;

    AccountStatus status;

    @JsonProperty("cookie-sync")
    AccountCookieSyncConfig cookieSync;

    public Account merge(Account another) {
        return Account.builder()
                .id(ObjectUtils.defaultIfNull(id, another.id))
                .priceGranularity(ObjectUtils.defaultIfNull(priceGranularity, another.priceGranularity))
                .bannerCacheTtl(ObjectUtils.defaultIfNull(bannerCacheTtl, another.bannerCacheTtl))
                .videoCacheTtl(ObjectUtils.defaultIfNull(videoCacheTtl, another.videoCacheTtl))
                .eventsEnabled(ObjectUtils.defaultIfNull(eventsEnabled, another.eventsEnabled))
                .enforceCcpa(ObjectUtils.defaultIfNull(enforceCcpa, another.enforceCcpa))
                .gdpr(ObjectUtils.defaultIfNull(gdpr, another.gdpr))
                .analyticsSamplingFactor(ObjectUtils.defaultIfNull(
                        analyticsSamplingFactor, another.analyticsSamplingFactor))
                .truncateTargetAttr(ObjectUtils.defaultIfNull(truncateTargetAttr, another.truncateTargetAttr))
                .defaultIntegration(ObjectUtils.defaultIfNull(defaultIntegration, another.defaultIntegration))
                .analyticsConfig(ObjectUtils.defaultIfNull(analyticsConfig, another.analyticsConfig))
                .bidValidations(ObjectUtils.defaultIfNull(bidValidations, another.bidValidations))
                .status(ObjectUtils.defaultIfNull(status, another.status))
                .cookieSync(ObjectUtils.defaultIfNull(cookieSync, another.cookieSync))
                .build();
    }

    public static Account empty(String id) {
        return Account.builder()
                .id(id)
                .build();
    }
}
