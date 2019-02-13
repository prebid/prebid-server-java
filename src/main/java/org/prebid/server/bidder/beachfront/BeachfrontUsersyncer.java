package org.prebid.server.bidder.beachfront;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

/**
 * Beachfront {@link Usersyncer} implementation
 */
public class BeachfrontUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;

    public BeachfrontUsersyncer(String usersyncUrl, String type, Boolean supportCORS, String externalUrl,
                                String platformId) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(type),
                Objects.requireNonNull(supportCORS), Objects.requireNonNull(externalUrl),
                Objects.requireNonNull(platformId));
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl and platformId
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String type, Boolean supportCORS,
                                                   String externalUrl, String platformId) {
        final String redirectUri = HttpUtil.encodeUrl(externalUrl)
                + "%2Fsetuid%3Fbidder%3Dbeachfront%26gdpr%3D{{gdpr}}%26gdpr_consent%3D{{gdpr_consent}}"
                + "%26uid%3D%5Bio_cid%5D";

        return UsersyncInfo.of(String.format("%s%s&gdpr={{gdpr}}&gc={{gdpr_consent}}&gce=1&url=%s",
                usersyncUrl, platformId, redirectUri), type, supportCORS);
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
