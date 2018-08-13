package org.prebid.server.bidder.openx;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

/**
 * OpenX {@link Usersyncer} implementation
 */
public class OpenxUsersyncer implements Usersyncer {
    private final UsersyncInfo usersyncInfo;

    public OpenxUsersyncer(String usersyncUrl, String externalUrl) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl and externalUrl
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri = HttpUtil.encodeUrl(externalUrl)
                + "%2Fsetuid%3Fbidder%3Dopenx%26gdpr%3D{{gdpr}}%26gdpr_consent%3D{{gdpr_consent}}%26uid%3D%24%7BUID%7D";

        return UsersyncInfo.of(String.format("%s%s", usersyncUrl, redirectUri), "redirect", false);
    }

    /**
     * Returns OpenX cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "openx";
    }

    /**
     * Returns OpenX {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
