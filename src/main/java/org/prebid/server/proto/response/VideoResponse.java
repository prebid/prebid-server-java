package org.prebid.server.proto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.response.ExtAdPod;

import java.util.List;

@Value(staticConstructor = "of")
public class VideoResponse {

    @JsonProperty("adPods")
    List<ExtAdPod> adPods;

    ExtAmpVideoResponse ext;
}

