package org.prebid.server.bidder.beachfront;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;

import java.util.Objects;

/**
 * Beachfront {@link Usersyncer} implementation
 */
public class BeachfrontUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;

    public BeachfrontUsersyncer(String usersyncUrl, String platformId) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(platformId));
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl and platformId
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String platformId) {
        return UsersyncInfo.of(String.format("%s%s", usersyncUrl, platformId), "iframe", false);
    }

    /**
     * Returns Beachfront cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "beachfront";
    }

    /**
     * Returns Beachfront {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
