package org.prebid.server.proto.openrtb.ext.request.mobfoxpb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.mobfoxpb
 */
@Value(staticConstructor = "of")
public class ExtImpMobfoxpb {

    @JsonProperty("TagID")
    String tagId;

    String key;
}
