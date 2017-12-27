package org.rtb.vexing.model.openrtb.ext.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Defines the contract for bidresponse.ext.usersync.{bidder}
 */
@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class ExtResponseSyncData {

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
