package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value
public class Channel {

    /**
     * A unique identifier assigned by the publisher. This may not be
     * a unique identifier across all supply sources.
     */
    String id;

    /**
     * Channel the content is on (e.g., a local channel like “WABC-TV")
     */
    String name;

    /**
     * The primary domain of the channel (e.g. “abc7ny.com” in the
     * case of the local channel WABC-TV). It is recommended to
     * include the top private domain (PSL+1) for DSP targeting
     * normalization purposes.
     */
    String domain;

    /**
     * Placeholder for exchange-specific extensions to OpenRTB.
     */
    ObjectNode ext;
}
