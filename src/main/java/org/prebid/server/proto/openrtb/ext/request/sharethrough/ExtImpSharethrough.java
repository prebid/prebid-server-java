package org.prebid.server.proto.openrtb.ext.request.sharethrough;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidRequest.imp[i].ext.sharethrough
 */
@AllArgsConstructor(
        staticName = "of"
)
@Value
public class ExtImpSharethrough {
    @JsonProperty("pkey")
    String pkey;

    @JsonProperty("iframe")
    Boolean iframe;

    @JsonProperty("iframeSize")
    List<Integer> iframeSize;
}
