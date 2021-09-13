package org.prebid.server.bidder.huaweiads.model.xnative;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Builder
@NoArgsConstructor
@Setter
@AllArgsConstructor(staticName = "of")
public class XnativeLink {
    private String url;
    private List<String> clickTrackers;
    private String fallback;
    private ObjectNode ext;
}
