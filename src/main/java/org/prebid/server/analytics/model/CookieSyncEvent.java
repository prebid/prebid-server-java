package org.prebid.server.analytics.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.response.BidderUsersyncStatus;

import java.util.Collections;
import java.util.List;

/**
 * Represents a transaction at /cookie_sync endpoint.
 */
@Builder
@Value
public class CookieSyncEvent {

    Integer status;

    List<String> errors;

    List<BidderUsersyncStatus> bidderStatus;

    public static CookieSyncEvent error(int status, String message) {
        return builder().status(status).errors(Collections.singletonList(message)).build();
    }
}
