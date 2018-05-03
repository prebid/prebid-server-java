package org.prebid.server.bidder.sovrn;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

public class SovrnUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;

    public SovrnUsersyncer(String usersyncUrl, String externalUrl) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));
    }

    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri = HttpUtil.encodeUrl("%s/setuid?bidder=sovrn&uid=", externalUrl);

        return UsersyncInfo.of(String.format("%sredir=%s", usersyncUrl, redirectUri), "redirect", false);
    }

    @Override
    public String cookieFamilyName() {
        return "sovrn";
    }

    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
