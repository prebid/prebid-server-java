package org.prebid.server.bidder.ix;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;

import java.util.Objects;

/**
 * ix {@link Usersyncer} implementation
 */
public class IxUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;

    public IxUsersyncer(String usersyncUrl) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl));
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl) {
        return UsersyncInfo.of(usersyncUrl, "redirect", false);
    }

    /**
     * Returns ix cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "ix";
    }

    /**
     * Returns ix {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
