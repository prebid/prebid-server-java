package org.prebid.server.spring.config.bidder.util;

import org.prebid.server.bidder.UsersyncMethod;
import org.prebid.server.bidder.UsersyncMethodType;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.spring.config.bidder.model.usersync.UsersyncBidderRegulationScopeProperties;
import org.prebid.server.spring.config.bidder.model.usersync.UsersyncConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.usersync.UsersyncMethodConfigurationProperties;

import java.util.Objects;

public class UsersyncerUtil {

    private UsersyncerUtil() {
    }

    public static Usersyncer create(UsersyncConfigurationProperties usersync) {

        final String cookieFamilyName = usersync.getCookieFamilyName();
        final UsersyncBidderRegulationScopeProperties skipwhenConfig = usersync.getSkipwhen();

        return Usersyncer.of(
                usersync.getEnabled(),
                cookieFamilyName,
                toMethod(UsersyncMethodType.IFRAME, usersync.getIframe()),
                toMethod(UsersyncMethodType.REDIRECT, usersync.getRedirect()),
                skipwhenConfig != null && skipwhenConfig.isGdpr(),
                skipwhenConfig == null ? null : skipwhenConfig.getGppSid());
    }

    private static UsersyncMethod toMethod(UsersyncMethodType type,
                                           UsersyncMethodConfigurationProperties properties) {

        if (properties == null) {
            return null;
        }

        return UsersyncMethod.builder()
                .type(type)
                .usersyncUrl(Objects.requireNonNull(properties.getUrl()))
                .uidMacro(properties.getUidMacro())
                .supportCORS(properties.getSupportCors())
                .formatOverride(properties.getFormatOverride())
                .build();
    }
}
