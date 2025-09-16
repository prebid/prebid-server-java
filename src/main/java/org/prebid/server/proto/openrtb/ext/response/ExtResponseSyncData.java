package org.prebid.server.proto.openrtb.ext.response;

import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidresponse.ext.usersync.{bidder}
 */
@Value
class ExtResponseSyncData {

    CookieStatus status;

    /**
     * Must have length > 0
     */
    List<ExtUserSync> syncs;

    /**
     * Describes the allowed values for bidresponse.ext.usersync.{bidder}.status
     */
    public enum CookieStatus {
        none, expired, available
    }
}
