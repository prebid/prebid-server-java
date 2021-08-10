package org.prebid.server.proto.openrtb.ext.request.adagio;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.adagio
 */

@Builder
@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpAdagio {

    @JsonProperty("organizationId")
    Integer organizationId;

    @JsonProperty("site")
    String site;

    @JsonProperty("placement")
    String placement;

}
