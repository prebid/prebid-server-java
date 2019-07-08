package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.context
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpContext {

    String keywords;

    String search;

    ObjectNode data;
}
