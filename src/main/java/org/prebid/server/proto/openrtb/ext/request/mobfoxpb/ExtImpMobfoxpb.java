package org.prebid.server.proto.openrtb.ext.request.mobfoxpb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.mobfoxpb
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpMobfoxpb {

    @JsonProperty("TagID")
    String tagId;

    String key;
}
