package org.prebid.server.bidder.pulsepoint;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

public class PulsepointUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;

    public PulsepointUsersyncer(String usersyncUrl, String externalUrl) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));
    }

    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri = HttpUtil.encodeUrl("%s/setuid?bidder=pulsepoint&uid=%s", externalUrl,
                "%%VGUID%%");
        return UsersyncInfo.of(String.format("%s%s", usersyncUrl, redirectUri), "redirect", false);
    }

    @Override
    public String cookieFamilyName() {
        return "pulsepoint";
    }

    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
