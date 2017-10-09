package org.rtb.vexing.model.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public final class Bid {

    /* Unique bid ID for this bidder for this slot. */
    String bidId;

    /* Unique code for an adapter to call. */
    String bidder;

    /* Optional params to send to the adapter. */
    ObjectNode params;
}
