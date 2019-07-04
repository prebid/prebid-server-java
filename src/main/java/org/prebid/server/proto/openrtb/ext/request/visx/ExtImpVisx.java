package org.prebid.server.proto.openrtb.ext.request.visx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidRequest.imp[i].ext.visx
 */
@AllArgsConstructor(
        staticName = "of"
)
@Value
public class ExtImpVisx {
    @JsonProperty("uid")
    Integer uid;

    @JsonProperty("size")
    List<Integer> size;
}
