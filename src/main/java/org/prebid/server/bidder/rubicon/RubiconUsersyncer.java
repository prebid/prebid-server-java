package org.prebid.server.bidder.rubicon;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;

import java.util.Objects;

public class RubiconUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;

    public RubiconUsersyncer(String usersyncUrl) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl));
    }

    private static UsersyncInfo createUsersyncInfo(String usersyncUrl) {
        return UsersyncInfo.of(usersyncUrl, "redirect", false);
    }

    @Override
    public String cookieFamilyName() {
        return "rubicon";
    }

    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
