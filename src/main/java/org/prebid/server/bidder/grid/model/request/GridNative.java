package org.prebid.server.bidder.grid.model.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Native;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class GridNative extends Native {

    ObjectNode requestNative;
}
