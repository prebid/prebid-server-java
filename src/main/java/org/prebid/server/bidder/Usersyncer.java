package org.prebid.server.bidder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Usersyncer {

    public static final Usersyncer EMPTY = new Usersyncer();

    private String cookieFamilyName;

    private String usersyncUrl;

    private String redirectUrl;

    private String type;

    private boolean supportCORS;

    public Usersyncer(String cookieFamilyName, String usersyncUrl, String redirectUrl, String externalUri,
                      String type, boolean supportCORS) {
        this.cookieFamilyName = cookieFamilyName;
        this.usersyncUrl = Objects.requireNonNull(usersyncUrl);
        this.redirectUrl = StringUtils.isNotBlank(redirectUrl)
                ? HttpUtil.validateUrl(externalUri) + redirectUrl
                : StringUtils.EMPTY;
        this.type = type;
        this.supportCORS = supportCORS;
    }
}
