package org.prebid.server.bidder.huaweiads.model.xnative;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Setter
@Getter
public class XnativeImage {
    private Integer imageAssetType;
    private String url;
    private Integer w;
    private Integer h;
    private ObjectNode ext;
}
