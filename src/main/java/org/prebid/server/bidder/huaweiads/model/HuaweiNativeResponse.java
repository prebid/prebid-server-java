package org.prebid.server.bidder.huaweiads.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.*;
import org.prebid.server.bidder.huaweiads.model.xnative.XnativeAsset;
import org.prebid.server.bidder.huaweiads.model.xnative.XnativeLink;
import org.prebid.server.bidder.huaweiads.model.xnative.request.EventTracker;

import java.util.List;

@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class HuaweiNativeResponse {
    private String ver;
    private List<XnativeAsset> assets;
    private String AssetsURL;
    private String DCOURL;
    private XnativeLink link;
    private List<String> impTrackers;
    private String jSTracker;
    private List<EventTracker> eventTrackers;
    private String privacy;
    private ObjectNode ext;

}
