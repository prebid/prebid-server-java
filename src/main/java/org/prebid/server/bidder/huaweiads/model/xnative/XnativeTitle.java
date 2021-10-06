package org.prebid.server.bidder.huaweiads.model.xnative;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class XnativeTitle {

    String text;

    Integer len;

    ObjectNode ext;
}

