package org.prebid.server.bidder.index;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;

import java.util.Objects;

/**
 * IndexExchange {@link Usersyncer} implementation
 */
public class IndexUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;
    private final boolean pbsEnforcesGdpr;

    public IndexUsersyncer(String usersyncUrl, boolean pbsEnforcesGdpr) {
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
     * Returns IndexExchange cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "indexExchange";
    }

    /**
     * Returns IndexExchange GDPR vendor ID
     */
    @Override
    public int gdprVendorId() {
        return 10;
    }

    /**
     * Returns if Index enforced to gdpr by pbs
     */
    @Override
    public boolean pbsEnforcesGdpr() {
        return pbsEnforcesGdpr;
    }

    /**
     * Returns IndexExchange {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
