package org.prebid.server.bidder.unicorn.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class UnicornImpExtContext {

    ObjectNode data;
}
