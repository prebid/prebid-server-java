package org.prebid.server.bidder.eplanning;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

/**
 * Eplanning {@link Usersyncer} implementation
 */
public class EplanningUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;
    private final boolean pbsEnforcesGdpr;

    public EplanningUsersyncer(String usersyncUrl, String externalUrl, boolean pbsEnforcesGdpr) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));
        this.pbsEnforcesGdpr = pbsEnforcesGdpr;
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl and externalUrl
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri = HttpUtil.encodeUrl("%s/setuid?bidder=eplanning&uid=$UID", externalUrl);
        return UsersyncInfo.of(String.format("%s%s", usersyncUrl, redirectUri), "redirect", false);
    }

    /**
     * Returns Eplanning cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "eplanning";
    }

    /**
     * Returns Eplanning GDPR vendor ID
     */
    @Override
    public int gdprVendorId() {
        return 0;
    }

    /**
     * Returns if Eplanning enforced to gdpr by pbs
     */
    @Override
    public boolean pbsEnforcesGdpr() {
        return pbsEnforcesGdpr;
    }

    /**
     * Returns Eplanning {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
