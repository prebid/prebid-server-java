package org.prebid.server.proto.response;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class CookieSyncResponse {

    String status;

    List<BidderUsersyncStatus> bidderStatus;
}
