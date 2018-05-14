package org.prebid.server.bidder.openx;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

/**
 * OpenX {@link Usersyncer} implementation
 */
public class OpenxUsersyncer implements Usersyncer {
    private final UsersyncInfo usersyncInfo;

    public OpenxUsersyncer(String usersyncUrl, String externalUrl) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl and externalUrl
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUrl = HttpUtil.encodeUrl("%s/setuid?bidder=openx&uid=${UID}", externalUrl);
        final String url = String.format("%s%s", usersyncUrl, redirectUrl);
        return UsersyncInfo.of(url, "redirect", false);
    }

    /**
     * Returns OpenX cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "openx";
    }

    /**
     * Returns OpenX GDPR vendor ID
     */
    @Override
    public int gdprVendorId() {
        return 69;
    }

    /**
     * Returns OpenX {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
