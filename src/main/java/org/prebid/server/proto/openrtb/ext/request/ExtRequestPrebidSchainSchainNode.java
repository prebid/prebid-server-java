package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.ext.prebid.schains
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestPrebidSchainSchainNode {

    /**
     * Defines the contract for bidrequest.ext.prebid.schains.asi
     */
    String asi;

    /**
     * Defines the contract for bidrequest.ext.prebid.schains.sid
     */
    String sid;

    /**
     * Defines the contract for bidrequest.ext.prebid.schains.hp
     */
    Integer hp;

    /**
     * Defines the contract for bidrequest.ext.prebid.schains.rid
     */
    String rid;

    /**
     * Defines the contract for bidrequest.ext.prebid.schains.name
     */
    String name;

    /**
     * Defines the contract for bidrequest.ext.prebid.schains.domain
     */
    String domain;

    /**
     * Defines the contract for bidrequest.ext.prebid.schains.ext
     */
    ObjectNode ext;
}
