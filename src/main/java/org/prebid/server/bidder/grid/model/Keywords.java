package org.prebid.server.bidder.grid.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class Keywords {

    public static Keywords empty() {
        return Keywords.of(null, null);
    }

    ObjectNode user;

    ObjectNode site;
}
