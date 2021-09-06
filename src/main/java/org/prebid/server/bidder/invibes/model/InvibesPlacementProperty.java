package org.prebid.server.bidder.invibes.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.Format;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class InvibesPlacementProperty {

    @JsonProperty("Formats")
    List<Format> formats;

    @JsonProperty("ImpID")
    String impId;
}
