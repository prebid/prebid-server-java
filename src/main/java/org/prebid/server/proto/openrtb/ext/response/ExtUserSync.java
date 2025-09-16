package org.prebid.server.proto.openrtb.ext.response;

import lombok.Value;

/**
 * Defines the contract for bidresponse.ext.usersync.{bidder}.syncs[i]
 */
@Value
class ExtUserSync {

    String url;

    UserSyncType type;

    /**
     * Describes the allowed values for bidresponse.ext.usersync.{bidder}.syncs[i].type
     */
    public enum UserSyncType {
        iframe, pixel
    }
}
