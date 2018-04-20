package org.prebid.server.bidder.facebook;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;

import java.util.Objects;

public class FacebookUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;

    public FacebookUsersyncer(String usersyncUrl) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl));
    }

    private static UsersyncInfo createUsersyncInfo(String usersyncUrl) {
        return UsersyncInfo.of(usersyncUrl, "redirect", false);
    }

    @Override
    public String cookieFamilyName() {
        return "audienceNetwork";
    }

    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
