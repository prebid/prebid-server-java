package org.prebid.server.bidder.adkerneladn;

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

/**
 * AdkernelAdn {@link Usersyncer} implementation
 */
public class AdkernelAdnUsersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;

    public AdkernelAdnUsersyncer(String usersyncUrl, String externalUrl) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl and externalUrl
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri = HttpUtil.encodeUrl(externalUrl)
                + "%2Fsetuid%3Fbidder%3DadkernelAdn%26gdpr%3D{{gdpr}}%26gdpr_consent%3D{{gdpr_consent}}"
                + "%26uid%3D%24%7BUID%7D";

        return UsersyncInfo.of(String.format("%s%s", usersyncUrl, redirectUri), "redirect", false);
    }

    /**
     * Returns AdkernelAdn cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "adkernelAdn";
    }

    /**
     * Returns AdkernelAdn {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}
