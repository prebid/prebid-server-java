package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.context
 */
@Value(staticConstructor = "of")
public class ExtImpContext {

    String keywords;

    String search;

    ObjectNode data;
}
