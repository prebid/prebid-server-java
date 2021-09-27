package org.prebid.server.proto.openrtb.ext.request.marsmedia;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.marsmedia
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpMarsmedia {

    @JsonAlias("zoneId")
    String zone;
}
