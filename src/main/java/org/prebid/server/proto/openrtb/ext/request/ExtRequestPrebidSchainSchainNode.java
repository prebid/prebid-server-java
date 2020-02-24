package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.ext.prebid.schain.schain.nodes
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestPrebidSchainSchainNode {
    /**
     * Defines the contract for bidrequest.ext.prebid.schain.schain.asi
     */
    String asi;
    /**
     * Defines the contract for bidrequest.ext.prebid.schain.schain.sid
     */

    String sid;

    /**
     * Defines the contract for bidrequest.ext.prebid.schain.schain.hp
     */
    Integer hp;

    /**
     * Defines the contract for bidrequest.ext.prebid.schain.schain.rid
     */
    String rid;

    /**
     * Defines the contract for bidrequest.ext.prebid.schain.schain.name
     */
    String name;

    /**
     * Defines the contract for bidrequest.ext.prebid.schain.schain.domain
     */
    String domain;

    /**
     * Defines the contract for bidrequest.ext.prebid.schain.schain.ext
     */
    ObjectNode ext;
}
