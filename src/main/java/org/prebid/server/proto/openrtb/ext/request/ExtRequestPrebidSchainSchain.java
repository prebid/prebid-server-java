package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.ext.prebid.schain.schain
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestPrebidSchainSchain {
    /**
     * Defines the contract for bidrequest.ext.prebid.schain.schain.ver
     */
    String ver;
    /**
     * Defines the contract for bidrequest.ext.prebid.schain.schain.complete
     */
    Integer comlete;
    /**
     * Defines the contract for bidrequest.ext.prebid.schain.schain.nodes
     */
    List<ExtRequestPrebidSchainSchainNode> nodes;
    /**
     * Defines the contract for bidrequest.ext.prebid.schain.schain.nodes
     */
    ObjectNode ext;
}
