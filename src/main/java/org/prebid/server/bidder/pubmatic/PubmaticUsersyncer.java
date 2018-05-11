package org.prebid.server.bidder.pubmatic;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

/**
 * Pubmatic {@link Usersyncer} implementation
 */
public class PubmaticUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;

    public PubmaticUsersyncer(String usersyncUrl, String externalUrl) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl and externalUrl
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri = HttpUtil.encodeUrl("%s/setuid?bidder=pubmatic&uid=", externalUrl);
        return UsersyncInfo.of(String.format("%s%s", usersyncUrl, redirectUri), "iframe", false);
    }

    /**
     * Returns Pubmatic cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "pubmatic";
    }

    /**
     * Returns Pubmatic GDPR vendor ID
     */
    @Override
    public int gdprVendorId() {
        return 76;
    }

    /**
     * Returns Pubmatic {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
