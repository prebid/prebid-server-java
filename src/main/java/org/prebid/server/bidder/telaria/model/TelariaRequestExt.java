package org.prebid.server.bidder.telaria.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class TelariaRequestExt {

    ObjectNode extra;
}
