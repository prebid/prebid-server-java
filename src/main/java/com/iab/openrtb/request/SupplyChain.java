package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class SupplyChain {

    /**
     * Flag indicating whether the chain contains all nodes involved
     * in the transaction leading back to the owner of the site, app
     * or other medium of the inventory, where 0 = no, 1 = yes.
     */
    Integer complete;

    /**
     * Array of SupplyChainNode objects in the order of the chain. In a
     * complete supply chain, the first node represents the initial
     * advertising system and seller ID involved in the transaction, i.e.
     * the owner of the site, app, or other medium. In an incomplete
     * supply chain, it represents the first known node. The last node
     * represents the entity sending this bid request.
     */
    List<SupplyChainNode> nodes;

    /**
     * Version of the supply chain specification in use, in the format
     * of “major.minor”. For example, for version 1.0 of the spec,
     * use the string “1.0”.
     */
    String ver;

    /**
     * Placeholder for advertising-system specific extensions to this object.
     */
    ObjectNode ext;
}
