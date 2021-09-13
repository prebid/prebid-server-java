package org.prebid.server.bidder.huaweiads.model.xnative;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@NoArgsConstructor
@Setter
@AllArgsConstructor(staticName = "of")
public class XnativeData {
    private Integer dataAssetType;
    private Integer len;
    private String label;
    private String value;
    private ObjectNode ext;

}
