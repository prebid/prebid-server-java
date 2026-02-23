package org.prebid.server.bidder.adnuntius.model.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class AdnuntiusNativeRequest {

    ObjectNode ortb;

}
