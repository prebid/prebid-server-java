package org.prebid.server.bidder.telaria.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class TelariaRequestExt {

    ObjectNode extra;
}
