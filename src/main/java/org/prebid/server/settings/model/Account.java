package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class Account {

    String id;

    String priceGranularity;

    Integer bannerCacheTtl;

    Integer videoCacheTtl;

    Boolean eventsEnabled;

    AccountDebugConfig debug;

    Boolean enforceCcpa;

    AccountGdprConfig gdpr;

    AccountCcpaConfig ccpa;

    Integer analyticsSamplingFactor;

    Integer truncateTargetAttr;

    String defaultIntegration;

    AccountAnalyticsConfig analyticsConfig;

    AccountBidValidationConfig bidValidations;

    AccountStatus status;

    AccountAuctionConfig auction;

    AccountPrivacyConfig privacy;

    AccountAnalyticsConfig analytics;

    @JsonProperty("cookie-sync")
    AccountCookieSyncConfig cookieSync;

    AccountHooksConfiguration hooks;

    public static Account empty(String id) {
        return Account.builder()
                .id(id)
                .build();
    }
}
