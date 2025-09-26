package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class Account {

    String id;

    AccountStatus status;

    AccountAuctionConfig auction;

    AccountPrivacyConfig privacy;

    AccountAnalyticsConfig analytics;

    AccountMetricsConfig metrics;

    @JsonAlias("cookie-sync")
    AccountCookieSyncConfig cookieSync;

    AccountHooksConfiguration hooks;

    AccountSettings settings;

    @JsonAlias("alternate-bidder-codes")
    AccountAlternateBidderCodes alternateBidderCodes;

    AccountVtrackConfig vtrack;

    public static Account empty(String id) {
        return Account.builder().id(id).build();
    }
}
