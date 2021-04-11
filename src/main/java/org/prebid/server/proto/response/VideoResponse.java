package org.prebid.server.proto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.response.ExtAdPod;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderMessage;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;

import java.util.List;
import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class VideoResponse {

    @JsonProperty("adPods")
    List<ExtAdPod> adPods;

    ExtResponseDebug extResponseDebug;

    Map<String, List<ExtBidderMessage>> errors;

    ObjectNode ext;
}

