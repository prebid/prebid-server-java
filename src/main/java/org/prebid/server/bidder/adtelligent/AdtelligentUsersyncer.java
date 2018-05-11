package org.prebid.server.bidder.adtelligent;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

/**
 * Adtelligent {@link Usersyncer} implementation
 */
public class AdtelligentUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;

    public AdtelligentUsersyncer(String usersyncUrl, String externalUrl) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl and externalUrl
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri = HttpUtil.encodeUrl("%s/setuid?bidder=adtelligent&uid={uid}", externalUrl);
        return UsersyncInfo.of(String.format("%s%s", usersyncUrl, redirectUri), "redirect", false);
    }

    /**
     * Returns Adtelligent cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "adtelligent";
    }

    /**
     * Returns Adtelligent GDPR vendor ID
     */
    @Override
    public int gdprVendorId() {
        return 0;
    }

    /**
     * Returns Adtelligent {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
