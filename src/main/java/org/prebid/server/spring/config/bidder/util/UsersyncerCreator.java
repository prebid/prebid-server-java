package org.prebid.server.spring.config.bidder.util;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.spring.config.bidder.model.UsersyncConfigurationProperties;
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
        final Usersyncer usersyncer = Usersyncer.of(
                usersync.getCookieFamilyName(),
                toPrimaryMethod(usersync, externalUrl),
                toSecondaryMethod(usersync, externalUrl));

        if (StringUtils.isBlank(usersyncer.getPrimaryMethod().getUsersyncUrl())
                && usersyncer.getSecondaryMethod() != null) {
            throw new IllegalArgumentException(String.format(
                    "Invalid usersync configuration: primary method is missing while secondary is present. "
                            + "Configuration: %s",
                    usersync));
        }

        return usersyncer;
    }

    private static Usersyncer.UsersyncMethod toPrimaryMethod(UsersyncConfigurationProperties usersync,
                                                             String externalUrl) {
        return toUsersyncMethod(
                usersync.getUrl(),
                usersync.getRedirectUrl(),
                externalUrl,
                usersync.getType(),
                usersync.getSupportCors());
    }

    private static Usersyncer.UsersyncMethod toSecondaryMethod(UsersyncConfigurationProperties usersync,
                                                               String externalUrl) {

        final UsersyncConfigurationProperties.SecondaryConfigurationProperties secondaryMethodConfig =
                usersync.getSecondary();

        return secondaryMethodConfig != null
                ? toUsersyncMethod(
                secondaryMethodConfig.getUrl(),
                secondaryMethodConfig.getRedirectUrl(),
                externalUrl,
                secondaryMethodConfig.getType(),
                secondaryMethodConfig.getSupportCors())
                : null;
    }

    private static Usersyncer.UsersyncMethod toUsersyncMethod(String usersyncUrl,
                                                              String redirectUrl,
                                                              String externalUri,
                                                              String type,
                                                              boolean supportCORS) {

        return Usersyncer.UsersyncMethod.of(
                type,
                Objects.requireNonNull(usersyncUrl),
                toRedirectUrl(redirectUrl, externalUri),
                supportCORS);
    }

    private static String toRedirectUrl(String redirectUrl, String externalUri) {
        return StringUtils.isNotBlank(redirectUrl)
                ? HttpUtil.validateUrl(externalUri) + redirectUrl
                : StringUtils.EMPTY;
    }
}
