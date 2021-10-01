package org.prebid.server.bidder.huaweiads.model.xnative;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.*;

@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class XnativeAsset {
    private Integer id;
    private Integer required;
    private XnativeTitle title;
    private XnativeImage image;
    private XnativeVideo video;
    private XnativeData data;
    private XnativeLink link;
    private ObjectNode ext;
}

