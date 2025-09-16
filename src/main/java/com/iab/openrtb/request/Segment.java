package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

/**
 * Segment objects are essentially key-value pairs that convey specific units of
 * data. The parent {@link Data} object is a collection of such values from a
 * given data provider.
 * The specific segment names and value options must be published by the
 * exchange <em>a priori</em> to its bidders.
 */
@Builder
@Value
public class Segment {

    /** ID of the data segment specific to the data provider. */
    String id;

    /** Name of the data segment specific to the data provider. */
    String name;

    /** String representation of the data segment value. */
    String value;

    /** Placeholder for exchange-specific extensions to OpenRTB. */
    ObjectNode ext;
}
