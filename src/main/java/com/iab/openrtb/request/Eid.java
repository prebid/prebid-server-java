package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

import java.util.List;

/**
 * Extended identifiers support in the OpenRTB specification allows buyers
 * to use audience data in real-time bidding. This object can contain one
 * or more {@link Uid}s from a single source or a technology provider. The
 * exchange should ensure that business agreements allow for the sending
 * of this data.
 */
@Value(staticConstructor = "of")
public class Eid {

    /**
     * Source or technology provider responsible for the set of included IDs. Expressed as a top-level domain.
     */
    String source;

    /**
     * Array of extended ID {@link Uid} objects from the given source.
     * Refer to 3.2.28 Extended Identifier UIDs
     */
    List<Uid> uids;

    /**
     * Placeholder for vendor specific extensions to this object
     */
    ObjectNode ext;
}
