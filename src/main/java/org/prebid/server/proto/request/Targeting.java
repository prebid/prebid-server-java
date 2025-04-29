package org.prebid.server.proto.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class Targeting {

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
}
