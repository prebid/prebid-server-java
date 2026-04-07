package org.prebid.server.spring.config.bidder.util;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.UsersyncMethod;
import org.prebid.server.bidder.UsersyncMethodType;
import org.prebid.server.bidder.UsersyncUtil;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.spring.config.bidder.model.usersync.CookieFamilySource;
import org.prebid.server.spring.config.bidder.model.usersync.UsersyncBidderRegulationScopeProperties;
import org.prebid.server.spring.config.bidder.model.usersync.UsersyncConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.usersync.UsersyncMethodConfigurationProperties;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class UsersyncerCreator {

    private final String externalUrl;

    public static UsersyncerCreator create(String externalUrl) {
        return new UsersyncerCreator(externalUrl);
    }

    public Usersyncer createAndValidate(String bidder,
                                        UsersyncConfigurationProperties usersync,
                                        CookieFamilySource cookieFamilySource) {

        final String cookieFamilyName = usersync.getCookieFamilyName();
        final UsersyncBidderRegulationScopeProperties skipwhenConfig = usersync.getSkipwhen();

        return Usersyncer.of(
                usersync.getEnabled(),
                bidder,
                cookieFamilyName,
                cookieFamilySource,
                toMethod(UsersyncMethodType.IFRAME, usersync.getIframe(), cookieFamilyName),
                toMethod(UsersyncMethodType.REDIRECT, usersync.getRedirect(), cookieFamilyName),
                skipwhenConfig != null && skipwhenConfig.isGdpr(),
                skipwhenConfig == null ? null : skipwhenConfig.getGppSid());
    }

    private UsersyncMethod toMethod(UsersyncMethodType type,
                                    UsersyncMethodConfigurationProperties properties,
                                    String cookieFamilyName) {

        if (properties == null) {
            return null;
        }

        return UsersyncMethod.builder()
                .type(type)
                .usersyncUrl(Objects.requireNonNull(properties.getUrl()))
                .redirectUrl(toRedirectUrl(cookieFamilyName, externalUrl, properties.getUidMacro()))
                .supportCORS(properties.getSupportCors())
                .formatOverride(properties.getFormatOverride())
                .build();
    }

    private static String toRedirectUrl(String cookieFamilyName, String externalUri, String uidMacro) {
        return UsersyncUtil.CALLBACK_URL_TEMPLATE.formatted(
                HttpUtil.validateUrl(externalUri), cookieFamilyName, StringUtils.defaultString(uidMacro));
    }
}
