package org.prebid.server.proto.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@AllArgsConstructor(staticName = "of")
public class Targeting {

    private static final Targeting EMPTY = Targeting.of(null, null, null);

    /*
     * Will be mapped to ext.prebid.data
     */

    List<String> bidders;
    /*
     * Will be mapped to site.ext.data
     */

    ObjectNode site;
    /*
     * Will be mapped to user.ext.data
     */

    ObjectNode user;

    public static Targeting empty() {
        return EMPTY;
    }
}
