package org.prebid.server.bidder.facebook;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;

import java.util.Objects;

/**
 * Facebook {@link Usersyncer} implementation
 */
public class FacebookUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;
    private final boolean pbsEnforcesGdpr;

    public FacebookUsersyncer(String usersyncUrl, boolean pbsEnforcesGdpr) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl));
        this.pbsEnforcesGdpr = pbsEnforcesGdpr;
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl) {
        return UsersyncInfo.of(usersyncUrl, "redirect", false);
    }

    /**
     * Returns Facebook cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "audienceNetwork";
    }

    /**
     * Returns Facebook GDPR vendor ID
     */
    @Override
    public int gdprVendorId() {
        return 0;
    }

    /**
     * Returns if Facebook enforced to gdpr by pbs
     */
    @Override
    public boolean pbsEnforcesGdpr() {
        return pbsEnforcesGdpr;
    }

    /**
     * Returns Facebook {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
