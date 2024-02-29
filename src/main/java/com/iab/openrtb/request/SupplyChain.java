package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

import java.util.List;

/**
 * This object is composed of a set of nodes where each node
 * represents a specific entity that participates in the
 * transacting of inventory. The entire chain of nodes from
 * beginning to end represents all entities who are involved
 * in the direct flow of payment for inventory. Detailed
 * implementation examples can be found <a href="https://github.com/InteractiveAdvertisingBureau/openrtb/blob/master/supplychainobject.md">here</a>.
 */
@Value(staticConstructor = "of")
public class SupplyChain {

    /**
     * Flag indicating whether the chain contains all nodes involved
     * in the transaction leading back to the owner of the site, app
     * or other medium of the inventory, where 0 = no, 1 = yes.
     * <p/> (required)
     */
    Integer complete;

    /**
     * Array of {@link SupplyChainNode} objects in the order of the chain. In a
     * complete supply chain, the first node represents the initial
     * advertising system and seller ID involved in the transaction, i.e.
     * the owner of the site, app, or other medium. In an incomplete
     * supply chain, it represents the first known node. The last node
     * represents the entity sending this bid request. <p/> (required)
     */
    List<SupplyChainNode> nodes;

    /**
     * Version of the supply chain specification in use, in the format
     * of “major.minor”. For example, for version 1.0 of the spec,
     * use the string “1.0”. <p/> (required)
     */
    String ver;

    /**
     * Placeholder for advertising-system specific extensions to this object.
     */
    ObjectNode ext;
}
