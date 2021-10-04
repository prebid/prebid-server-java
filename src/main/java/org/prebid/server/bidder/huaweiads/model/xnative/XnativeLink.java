package org.prebid.server.bidder.huaweiads.model.xnative;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
@AllArgsConstructor(staticName = "of")
public class XnativeLink {

    String url;
    List<String> clickTrackers;
    String fallback;
    ObjectNode ext;
}
