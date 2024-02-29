package org.prebid.server.spring.config.bidder.util;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.UsersyncMethod;
import org.prebid.server.bidder.UsersyncMethodType;
import org.prebid.server.bidder.UsersyncUtil;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.spring.config.bidder.model.usersync.CookieFamilySource;
import org.prebid.server.spring.config.bidder.model.usersync.UsersyncConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.usersync.UsersyncMethodConfigurationProperties;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;
import java.util.function.BiFunction;

public class UsersyncerCreator {

    private UsersyncerCreator() {
    }

    public static BiFunction<UsersyncConfigurationProperties, CookieFamilySource, Usersyncer> create(
            String externalUrl) {

        return (usersyncConfig, cookieFamilySource) ->
                createAndValidate(usersyncConfig, cookieFamilySource, externalUrl);
    }

    private static Usersyncer createAndValidate(UsersyncConfigurationProperties usersync,
                                                CookieFamilySource cookieFamilySource,
                                                String externalUrl) {

        final String cookieFamilyName = usersync.getCookieFamilyName();

        return Usersyncer.of(
                usersync.getEnabled(),
                cookieFamilyName,
                cookieFamilySource,
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
