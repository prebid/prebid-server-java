package org.prebid.server.bidder.sovrn;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

/**
 * Sovrn {@link Usersyncer} implementation
 */
public class SovrnUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;

    public SovrnUsersyncer(String usersyncUrl, String externalUrl) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl and externalUrl
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri = HttpUtil.encodeUrl(externalUrl)
                + "%2Fsetuid%3Fbidder%3Dsovrn%26gdpr%3D{{gdpr}}%26gdpr_consent%3D{{gdpr_consent}}%26uid%3D%24UID";

        return UsersyncInfo.of(String.format("%sredir=%s", usersyncUrl, redirectUri), "redirect", false);
    }

    /**
     * Returns Sovrn cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "sovrn";
    }

    /**
     * Returns Sovrn {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
