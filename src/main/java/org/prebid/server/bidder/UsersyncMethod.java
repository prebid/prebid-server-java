package org.prebid.server.bidder;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.util.Uri;

@Value
@Builder
public class UsersyncMethod {

    UsersyncMethodType type;

    Uri usersyncUrl;

    String uidMacro;

    boolean supportCORS;

    UsersyncFormat formatOverride;
}
