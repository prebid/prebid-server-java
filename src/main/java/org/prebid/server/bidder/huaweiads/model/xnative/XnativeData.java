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

    Integer dataAssetType;
    Integer len;
    String label;
    String value;
    ObjectNode ext;

}
