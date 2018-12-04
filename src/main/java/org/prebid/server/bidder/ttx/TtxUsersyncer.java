package org.prebid.server.bidder.ttx;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

/**
 * 33Across {@link Usersyncer} implementation
 */
public class TtxUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;

    public TtxUsersyncer(String usersyncUrl, String externalUrl, String partnerId) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl),
                Objects.requireNonNull(externalUrl), partnerId);
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl, externalUrl and partnerId
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl, String partnerId) {
        final String redirectUri = HttpUtil.encodeUrl(externalUrl)
                + "%2Fsetuid%3Fbidder%3Dttx%26gdpr%3D{{gdpr}}%26gdpr_consent%3D{{gdpr_consent}}%26uid%3D33XUSERID33X";
        final String syncerUrl = StringUtils.isBlank(partnerId)
                ? "/"
                : String.format("%s/?ri=%s&ru=%s", usersyncUrl, partnerId, redirectUri);

        return UsersyncInfo.of(syncerUrl, "redirect", false);
    }

    /**
     * Returns 33Arcoss cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "ttx";
    }

    /**
     * Returns 33Across {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
