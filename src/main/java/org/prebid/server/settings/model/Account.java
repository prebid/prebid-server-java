package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.activity.Activity;
import org.prebid.server.settings.model.activity.AccountActivityConfiguration;

import java.util.Map;

@Builder(toBuilder = true)
@Value
public class Account {

    String id;

    AccountStatus status;

    AccountAuctionConfig auction;

    AccountPrivacyConfig privacy;

    AccountAnalyticsConfig analytics;

    AccountMetricsConfig metrics;

    @JsonProperty("cookie-sync")
    AccountCookieSyncConfig cookieSync;

    AccountHooksConfiguration hooks;

    @JsonProperty("allowactivities")
    Map<Activity, AccountActivityConfiguration> activities;

    public static Account empty(String id) {
        return Account.builder()
                .id(id)
                .build();
    }
}
