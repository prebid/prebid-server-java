package org.prebid.server.bidder.pubmatic.proto;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class PubmaticRequestExt {

    ObjectNode wrapper;
}
