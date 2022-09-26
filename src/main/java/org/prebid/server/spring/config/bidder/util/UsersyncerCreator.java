package org.prebid.server.spring.config.bidder.util;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.UsersyncMethod;
import org.prebid.server.bidder.UsersyncMethodType;
import org.prebid.server.bidder.UsersyncUtil;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.spring.config.bidder.model.usersync.UsersyncConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.usersync.UsersyncMethodConfigurationProperties;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;
import java.util.function.Function;

public class UsersyncerCreator {

    private UsersyncerCreator() {
    }

    public static Function<UsersyncConfigurationProperties, Usersyncer> create(String externalUrl) {
        return usersyncConfig -> createAndValidate(usersyncConfig, externalUrl);
    }

    private static Usersyncer createAndValidate(UsersyncConfigurationProperties usersync, String externalUrl) {
        final String cookieFamilyName = usersync.getCookieFamilyName();

        return Usersyncer.of(
                cookieFamilyName,
                toMethod(UsersyncMethodType.IFRAME, usersync.getIframe(), cookieFamilyName, externalUrl),
                toMethod(UsersyncMethodType.REDIRECT, usersync.getRedirect(), cookieFamilyName, externalUrl));
    }

    private static UsersyncMethod toMethod(UsersyncMethodType type,
                                           UsersyncMethodConfigurationProperties properties,
                                           String cookieFamilyName,
                                           String externalUrl) {

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
