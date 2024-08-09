package org.prebid.server.bidder.criteo;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class CriteoIgsIgiExtBidResponse {

    ObjectNode config;
}
