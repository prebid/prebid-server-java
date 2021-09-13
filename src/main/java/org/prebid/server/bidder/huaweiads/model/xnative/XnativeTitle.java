package org.prebid.server.bidder.huaweiads.model.xnative;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;

@Builder
public class XnativeTitle {
    private String text;
    private Integer len;
    private ObjectNode ext;
}
