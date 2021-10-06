package org.prebid.server.bidder.huaweiads.model.xnative;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class XnativeLink {

    String url;

    List<String> clickTrackers;

    String fallback;

    ObjectNode ext;
}

