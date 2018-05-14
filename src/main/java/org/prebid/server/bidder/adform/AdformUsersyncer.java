package org.prebid.server.bidder.adform;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

/**
 * Adform {@link Usersyncer} implementation
 */
public class AdformUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;

    public AdformUsersyncer(String usersyncUrl, String externalUrl) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl and externalUrl
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri = HttpUtil.encodeUrl("%s/setuid?bidder=adform&uid=$UID", externalUrl);
        return UsersyncInfo.of(String.format("%s%s", usersyncUrl, redirectUri), "redirect", false);
    }

    /**
     * Returns Adform cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "adform";
    }

    /**
     * Returns Adform GDPR vendor ID
     */
    @Override
    public int gdprVendorId() {
        return 50;
    }

    /**
     * Returns Adform {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
