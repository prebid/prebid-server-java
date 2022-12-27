package org.prebid.server.proto.response;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.cookie.model.CookieSyncStatus;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class CookieSyncResponse {

    CookieSyncStatus status;

    List<BidderUsersyncStatus> bidderStatus;
}
