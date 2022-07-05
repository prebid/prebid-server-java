package org.prebid.server.spring.config.bidder.util;

import org.apache.commons.collections4.ListUtils;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.spring.config.bidder.model.UsersyncConfigurationProperties;
import org.prebid.server.util.HttpUtil;

import java.util.List;
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
        final List<Usersyncer.UsersyncMethod> usersyncMethods = ListUtils.emptyIfNull(usersync.getMethods()).stream()
                .map(config ->
                        Usersyncer.UsersyncMethod.of(
                                config.getType(),
                                Objects.requireNonNull(config.getUrl()),
                                toRedirectUrl(cookieFamilyName, externalUrl, config.getUidMacro()),
                                config.getSupportCors()))
                .toList();

        return Usersyncer.of(usersync.getCookieFamilyName(), usersyncMethods);
    }

    private static String toRedirectUrl(String cookieFamilyName, String externalUri, String uidMacro) {
        final String redirectUrl = "/setuid?bidder=" + cookieFamilyName
                + "&gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}&us_privacy={{us_privacy}}&uid="
                + uidMacro;

        return HttpUtil.validateUrl(externalUri) + redirectUrl;
    }
}
