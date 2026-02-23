package org.prebid.server.proto.response;

import lombok.Value;
import org.prebid.server.cookie.model.CookieSyncStatus;

import java.util.List;

@Value(staticConstructor = "of")
public class CookieSyncResponse {

    CookieSyncStatus status;

    List<BidderUsersyncStatus> bidderStatus;

    List<String> warnings;
}
