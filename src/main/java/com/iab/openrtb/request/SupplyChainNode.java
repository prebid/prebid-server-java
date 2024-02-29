package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

/**
 * This object is associated with a SupplyChain object as an array of nodes.
 * These nodes define the identity of an entity participating in the supply
 * chain of a bid request. Detailed implementation examples can be found
 * <a href="https://github.com/InteractiveAdvertisingBureau/openrtb/blob/master/supplychainobject.md">here</a>.
 */
@Value(staticConstructor = "of")
public class SupplyChainNode {

    /**
     * The canonical domain name of the SSP, Exchange, Header
     * Wrapper, etc system that bidders connect to. This may be
     * the operational domain of the system, if that is different than
     * the parent corporate domain, to facilitate WHOIS and
     * reverse IP lookups to establish clear ownership of the
     * delegate system. This should be the same value as used to
     * identify sellers in an ads.txt file if one exists.
     * <p/> (required)
     */
    String asi;

    /**
     * The identifier associated with the seller or reseller account
     * within the advertising system. This must contain the same value
     * used in transactions (i.e. OpenRTB bid requests) in the field
     * specified by the SSP/exchange. Typically, in OpenRTB, this is
     * publisher.id. For OpenDirect it is typically the publisher’s
     * organization ID.Should be limited to 64 characters in length.
     * <p/> (required)
     */
    String sid;

    /**
     * The OpenRTB RequestId of the request as issued by this seller.
     */
    String rid;

    /**
     * The name of the company (the legal entity) that is paid for
     * inventory transacted under the given seller_ID. This value is
     * optional and should NOT be included if it exists in the
     * advertising system’s sellers.json file.
     */
    String name;

    /**
     * The business domain name of the entity represented by this
     * node. This value is optional and should NOT be included if it
     * exists in the advertising system’s sellers.json file.
     */
    String domain;

    /**
     * Indicates whether this node will be involved in the flow of
     * payment for the inventory. When set to 1, the advertising
     * system in the asi field pays the seller in the sid field, who is
     * responsible for paying the previous node in the chain. When
     * set to 0, this node is not involved in the flow of payment for
     * the inventory. For version 1.0 of SupplyChain, this property
     * should always be 1. Implementers should ensure that they
     * propagate this field onwards when constructing SupplyChain
     * objects in bid requests sent to a downstream advertising system.
     */
    Integer hp;

    /**
     * Placeholder for advertising-system specific extensions to this object.
     */
    ObjectNode ext;
}
