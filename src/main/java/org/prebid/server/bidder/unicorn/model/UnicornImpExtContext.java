package org.prebid.server.bidder.unicorn.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class UnicornImpExtContext {

    ObjectNode data;
}
