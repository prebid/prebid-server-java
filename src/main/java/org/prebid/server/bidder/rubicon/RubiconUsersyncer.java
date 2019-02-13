package org.prebid.server.bidder.rubicon;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;

import java.util.Objects;

/**
 * Rubicon {@link Usersyncer} implementation
 */
public class RubiconUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;

    public RubiconUsersyncer(String usersyncUrl, String type, Boolean supportCORS) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl),
                Objects.requireNonNull(type),
                Objects.requireNonNull(supportCORS));
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String type, Boolean supportCORS) {
        return UsersyncInfo.of(usersyncUrl, type, supportCORS);
    }

    /**
     * Returns Rubicon cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "rubicon";
    }

    /**
     * Returns Rubicon {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
