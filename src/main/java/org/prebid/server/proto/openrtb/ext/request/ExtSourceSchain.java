package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.source.ext.schain
 */
@Value(staticConstructor = "of")
public class ExtSourceSchain {

    /**
     * Defines the contract for bidrequest.ext.prebid.schain.ver
     */
    String ver;

    /**
     * Defines the contract for bidrequest.ext.prebid.schain.complete
     */
    Integer complete;

    /**
     * Defines the contract for bidrequest.ext.prebid.schain.nodes
     */
    List<ExtRequestPrebidSchainSchainNode> nodes;

    /**
     * Defines the contract for bidrequest.ext.prebid.schain.ext
     */
    ObjectNode ext;
}
