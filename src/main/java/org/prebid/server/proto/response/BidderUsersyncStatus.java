package org.prebid.server.proto.response;

import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

@Builder
@Value
public class BidderUsersyncStatus {

    String bidder;

    String error;

    Boolean noCookie;

    UsersyncInfo usersync;

    public boolean isRejected() {
        return StringUtils.isNotBlank(error);
    }
}
