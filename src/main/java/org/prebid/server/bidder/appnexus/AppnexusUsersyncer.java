package org.prebid.server.bidder.appnexus;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

/**
 * Appnexus {@link Usersyncer} implementation
 */
public class AppnexusUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;
    private final boolean pbsEnforcesGdpr;

    public AppnexusUsersyncer(String usersyncUrl, String externalUrl, boolean pbsEnforcesGdpr) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));
        this.pbsEnforcesGdpr = pbsEnforcesGdpr;
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl and externalUrl
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri = HttpUtil.encodeUrl("%s/setuid?bidder=adnxs&uid=$UID", externalUrl);

        return UsersyncInfo.of(String.format("%s%s", usersyncUrl, redirectUri), "redirect", false);
    }

    /**
     * Returns Appnexus cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "adnxs";
    }

    /**
     * Returns Appnexus GDPR vendor ID
     */
    @Override
    public int gdprVendorId() {
        return 32;
    }

    /**
     * Returns if Appnexus enforced to gdpr by pbs
     */
    @Override
    public boolean pbsEnforcesGdpr() {
        return pbsEnforcesGdpr;
    }

    /**
     * Returns Appnexus {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
