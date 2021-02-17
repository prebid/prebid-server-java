package org.prebid.server.spring.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAnalyticsConfig;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountCookieSyncConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.AccountStatus;

@Data
@NoArgsConstructor
public class AccountConfigurationProperties {

    private AccountStatus status;

    private String auction;

    private String privacy;

    private String analytics;

    private String cookieSync;

    public Account toAccount(JacksonMapper mapper) {
        return Account.builder()
                .status(getStatus())
                .auction(toModel(mapper, getAuction(), AccountAuctionConfig.class))
                .privacy(toModel(mapper, getPrivacy(), AccountPrivacyConfig.class))
                .analytics(toModel(mapper, getAnalytics(), AccountAnalyticsConfig.class))
                .cookieSync(toModel(mapper, getCookieSync(), AccountCookieSyncConfig.class))
                .build();
    }

    private static <T> T toModel(JacksonMapper mapper, String source, Class<T> targetClass) {
        return source != null ? mapper.decodeValue(source, targetClass) : null;
    }
}
