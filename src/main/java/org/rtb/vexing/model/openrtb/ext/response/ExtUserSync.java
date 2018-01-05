package org.rtb.vexing.model.openrtb.ext.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * Defines the contract for bidresponse.ext.usersync.{bidder}.syncs[i]
 */
@ToString
@EqualsAndHashCode
@AllArgsConstructor(staticName = "of")
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class ExtUserSync {

    String url;

    UserSyncType type;

    /**
     * Describes the allowed values for bidresponse.ext.usersync.{bidder}.syncs[i].type
     */
    public enum UserSyncType {
        iframe, pixel
    }
}
