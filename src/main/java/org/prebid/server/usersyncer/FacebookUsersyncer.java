package org.prebid.server.usersyncer;

import org.prebid.server.bidder.BidderName;
import org.prebid.server.model.response.UsersyncInfo;

import java.util.Objects;

public class FacebookUsersyncer implements Usersyncer {

    private static final String NAME = BidderName.audienceNetwork.name();

    private final UsersyncInfo usersyncInfo;

    public FacebookUsersyncer(String usersyncUrl) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl));
    }

    private static UsersyncInfo createUsersyncInfo(String usersyncUrl) {
        return UsersyncInfo.of(usersyncUrl, "redirect", false);
    }

    @Override
    public String name() {
        return NAME;
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
