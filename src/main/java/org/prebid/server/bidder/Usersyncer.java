package org.prebid.server.bidder;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.response.UsersyncInfo;

@Data
@AllArgsConstructor
public class Usersyncer {

    String cookieFamilyName;

    String usersyncUrl;

    private String redirectUrl;

    private String type;

    private boolean supportCORS;

    public static Usersyncer create(String cookieFamilyName, String usersyncUrl, String redirectUrl, String externalUri,
                                    String type, boolean supportCors) {
        final String resolvedRedirectUrl = StringUtils.isBlank(redirectUrl)
                ? StringUtils.EMPTY
                : externalUri + redirectUrl;

        return new Usersyncer(cookieFamilyName, usersyncUrl, resolvedRedirectUrl, type, supportCors);
    }

    public UsersyncInfo.UsersyncInfoAssembler usersyncInfoAssembler() {
        return UsersyncInfo.UsersyncInfoAssembler.assembler(usersyncUrl, redirectUrl, type, supportCORS);
    }
}
