package org.prebid.server.bidder.huaweiads.model.xnative;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class XnativeAsset {

    Integer id;

    Integer required;

    XnativeTitle title;

    XnativeImage image;

    XnativeVideo video;

    XnativeData data;

    XnativeLink link;

    ObjectNode ext;
}

