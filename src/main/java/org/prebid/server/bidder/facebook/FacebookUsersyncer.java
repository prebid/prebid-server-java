package org.prebid.server.bidder.facebook;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;

import java.util.Objects;

/**
 * Facebook {@link Usersyncer} implementation
 */
public class FacebookUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;

    public FacebookUsersyncer(String usersyncUrl, String type, Boolean supportCORS) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(type),
                Objects.requireNonNull(supportCORS));
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String type, Boolean supportCORS) {
        return UsersyncInfo.of(usersyncUrl, type, supportCORS);
    }

    /**
     * Returns Facebook cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "audienceNetwork";
    }

    /**
     * Returns Facebook {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
