package org.prebid.server.spring.config.bidder.util;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.spring.config.bidder.model.UsersyncConfigurationProperties;

import java.util.function.Function;

public class UsersyncerCreator {

    private UsersyncerCreator() {
    }

    public static Function<UsersyncConfigurationProperties, Usersyncer> create(String externalUrl) {
        return usersyncConfig -> new Usersyncer(
                usersyncConfig.getCookieFamilyName(),
                usersyncConfig.getUrl(),
                usersyncConfig.getRedirectUrl(),
                externalUrl,
                usersyncConfig.getType(),
                usersyncConfig.getSupportCors());
    }
}
