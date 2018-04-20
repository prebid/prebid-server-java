package org.prebid.server.bidder.adtelligent;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

/**
 * Describes the behavior for {@link Usersyncer} implementations.
 */
public class AdtelligentUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;

    public AdtelligentUsersyncer(String usersyncUrl, String externalUrl) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl and externalUrl.
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri = HttpUtil.encodeUrl("%s/setuid?bidder=adtelligent&uid={uid}", externalUrl);
        return UsersyncInfo.of(String.format("%s%s", usersyncUrl, redirectUri), "redirect", false);
    }

    /**
     * Returns adtelligent cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "adtelligent";
    }

    /**
     * Returns adtelligent {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
