package org.prebid.server.spring.config.bidder.util;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.spring.config.bidder.model.UsersyncConfigurationProperties;

import java.util.function.Supplier;

public class UsersyncerCreator {

    private UsersyncerCreator() {

    }

    public static Supplier<Usersyncer> create(UsersyncConfigurationProperties usersync, String externalUrl) {
        return () -> new Usersyncer(
                usersync.getCookieFamilyName(),
                usersync.getUrl(),
                usersync.getRedirectUrl(),
                externalUrl,
                usersync.getType(),
                usersync.getSupportCors());
    }
}
