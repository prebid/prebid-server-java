package org.prebid.server.usersyncer;

import org.prebid.server.bidder.BidderName;
import org.prebid.server.model.response.UsersyncInfo;

import java.util.Objects;

public class IndexUsersyncer implements Usersyncer {

    private static final String NAME = BidderName.indexExchange.name();

    private final UsersyncInfo usersyncInfo;

    public IndexUsersyncer(String usersyncUrl) {
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
        return "indexExchange";
    }

    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
