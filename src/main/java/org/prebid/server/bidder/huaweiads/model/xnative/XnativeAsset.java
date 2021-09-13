package org.prebid.server.bidder.huaweiads.model.xnative;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Setter
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
