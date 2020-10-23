package org.prebid.server.settings.model;

import lombok.Builder;
import lombok.Value;

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

    public static Account empty(String id) {
        return Account.builder()
                .id(id)
                .build();
    }
}
