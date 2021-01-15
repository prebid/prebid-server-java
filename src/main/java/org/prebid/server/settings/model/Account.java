package org.prebid.server.settings.model;

import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.ObjectUtils;

@Builder
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

    public Account merge(Account another) {
        return Account.builder()
                .id(ObjectUtils.firstNonNull(id, another.id))
                .priceGranularity(ObjectUtils.firstNonNull(priceGranularity, another.priceGranularity))
                .bannerCacheTtl(ObjectUtils.firstNonNull(bannerCacheTtl, another.bannerCacheTtl))
                .videoCacheTtl(ObjectUtils.firstNonNull(videoCacheTtl, another.videoCacheTtl))
                .eventsEnabled(ObjectUtils.firstNonNull(eventsEnabled, another.eventsEnabled))
                .enforceCcpa(ObjectUtils.firstNonNull(enforceCcpa, another.enforceCcpa))
                .gdpr(ObjectUtils.firstNonNull(gdpr, another.gdpr))
                .analyticsSamplingFactor(ObjectUtils.firstNonNull(
                        analyticsSamplingFactor, another.analyticsSamplingFactor))
                .truncateTargetAttr(ObjectUtils.firstNonNull(truncateTargetAttr, another.truncateTargetAttr))
                .defaultIntegration(ObjectUtils.firstNonNull(defaultIntegration, another.defaultIntegration))
                .analyticsConfig(ObjectUtils.firstNonNull(analyticsConfig, another.analyticsConfig))
                .bidValidations(ObjectUtils.firstNonNull(bidValidations, another.bidValidations))
                .status(ObjectUtils.firstNonNull(status, another.status))
                .build();
    }

    public static Account empty(String id) {
        return Account.builder()
                .id(id)
                .build();
    }
}
