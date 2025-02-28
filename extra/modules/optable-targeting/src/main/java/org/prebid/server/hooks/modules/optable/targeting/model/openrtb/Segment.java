package org.prebid.server.hooks.modules.optable.targeting.model.openrtb;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value
public class Segment {

    String id;

    ObjectNode ext;
}
