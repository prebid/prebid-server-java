package org.prebid.server.bidder.lifestreet;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

/**
 * Lifestreet {@link Usersyncer} implementation
 */
public class LifestreetUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;
    private final boolean pbsEnforcesGdpr;

    public LifestreetUsersyncer(String usersyncUrl, String externalUrl, boolean pbsEnforcesGdpr) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));
        this.pbsEnforcesGdpr = pbsEnforcesGdpr;
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl and externalUrl
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri = HttpUtil.encodeUrl("%s/setuid?bidder=lifestreet&uid=$$visitor_cookie$$",
                externalUrl);
        return UsersyncInfo.of(String.format("%s%s", usersyncUrl, redirectUri), "redirect", false);
    }

    /**
     * Returns Lifestreet cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "lifestreet";
    }

    /**
     * Returns Lifestreet GDPR vendor ID
     */
    @Override
    public int gdprVendorId() {
        return 67;
    }

    /**
     * Returns if Lifestreet enforced to gdpr by pbs
     */
    @Override
    public boolean pbsEnforcesGdpr() {
        return pbsEnforcesGdpr;
    }

    /**
     * Returns Lifestreet {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
