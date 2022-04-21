package org.prebid.server.bidder.grid.model.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class Keywords {

    public static Keywords empty() {
        return Keywords.of(null, null);
    }

    ObjectNode user;

    ObjectNode site;
}
