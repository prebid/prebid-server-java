package org.prebid.server.bidder.rubicon;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;

import java.util.Objects;

/**
 * Rubicon {@link Usersyncer} implementation
 */
public class RubiconUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;

    public RubiconUsersyncer(String usersyncUrl) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl));
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl) {
        return UsersyncInfo.of(usersyncUrl, "redirect", false);
    }

    /**
     * Returns Rubicon cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "rubicon";
    }

    /**
     * Returns Rubicon GDPR vendor ID
     */
    @Override
    public int gdprVendorId() {
        return 52;
    }

    /**
     * Returns Rubicon {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
