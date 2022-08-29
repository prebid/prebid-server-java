package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

/**
 * This object contains a single user identifier provided as part of
 * extended identifiers. The exchange should ensure that business
 * agreements allow for the sending of this data.
 */
@Value(staticConstructor = "of")
public class Uid {

    /**
     * The identifier for the user.
     */
    String id;

    /**
     * Type of user agent the ID is from. It is highly recommended to set this, as
     * many DSPs separate app-native IDs from browser-based IDs and require a type
     * value for ID resolution. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list_agenttypes">List: Agent Types</a> in AdCOM 1.0
     */
    Integer atype;

    /**
     * Placeholder for vendor specific extensions to this object
     */
    ObjectNode ext;
}
