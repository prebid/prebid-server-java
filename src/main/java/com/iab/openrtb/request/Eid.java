package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

import java.util.List;

@Value
public class Eid {

    /**
     * Source or technology provider responsible for the set of included IDs. Expressed as a top-level domain.
     */
    String source;

    /**
     * Array of extended ID {@link Uid} objects from the given source.
     */
    List<Uid> uids;

    /**
     * Placeholder for vendor specific extensions to this object
     */
    ObjectNode ext;
}
