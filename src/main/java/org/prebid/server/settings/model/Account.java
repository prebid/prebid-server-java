package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.ObjectUtils;

@Builder(toBuilder = true)
@Value
public class Account {

    String id;

    AccountStatus status;

    AccountAuctionConfig auction;

    AccountPrivacyConfig privacy;

    AccountAnalyticsConfig analytics;

    @JsonProperty("cookie-sync")
    AccountCookieSyncConfig cookieSync;

    AccountHooksConfiguration hooks;

    public Account merge(Account another) {
        return Account.builder()
                .id(ObjectUtils.defaultIfNull(id, another.id))
                .status(ObjectUtils.defaultIfNull(status, another.status))
                .auction(ObjectUtils.defaultIfNull(auction, another.auction))
                .privacy(ObjectUtils.defaultIfNull(privacy, another.privacy))
                .analytics(ObjectUtils.defaultIfNull(analytics, another.analytics))
                .cookieSync(ObjectUtils.defaultIfNull(cookieSync, another.cookieSync))
                .build();
    }

    public static Account empty(String id) {
        return Account.builder()
                .id(id)
                .build();
    }
}
