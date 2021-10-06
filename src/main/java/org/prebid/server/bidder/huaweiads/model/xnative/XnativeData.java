package org.prebid.server.bidder.huaweiads.model.xnative;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class XnativeData {

    Integer dataAssetType;

    Integer len;

    String label;

    String value;

    ObjectNode ext;
}

