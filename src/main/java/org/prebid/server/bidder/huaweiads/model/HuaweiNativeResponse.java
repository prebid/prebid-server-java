package org.prebid.server.bidder.huaweiads.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

    String ver;
    List<XnativeAsset> assets;
    String assetsURL;
    String dcourl;
    XnativeLink link;
    List<String> impTrackers;
    String jSTracker;
    List<EventTracker> eventTrackers;
    String privacy;
    ObjectNode ext;
}
