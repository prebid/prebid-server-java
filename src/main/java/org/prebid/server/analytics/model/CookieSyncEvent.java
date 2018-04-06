package org.prebid.server.analytics.model;

import lombok.Value;
import org.prebid.server.proto.response.BidderStatus;

import java.util.List;

/**
 * Represents a transaction at /cookie_sync endpoint.
 */
@Value
public class CookieSyncEvent {

    Integer status;

    List<String> errors;

    // FIXME: extract cookie sync info POJO
    List<BidderStatus> bidderStatuses;
}
