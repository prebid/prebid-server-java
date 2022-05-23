package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value
public class Network {

    /**
     * A unique identifier assigned by the publisher. This may not be
     * a unique identifier across all supply sources.
     */
    String id;

    /**
     * Network the content is on (e.g., a TV network like “ABC")
     */
    String name;

    /**
     * The primary domain of the network (e.g. “abc.com” in the
     * case of thenetwork ABC). It is recommended to include the
     * top private domain (PSL+1) for DSP targeting normalization purposes.
     */
    String domain;

    /**
     * Placeholder for exchange-specific extensions to OpenRTB.
     */
    ObjectNode ext;
}
