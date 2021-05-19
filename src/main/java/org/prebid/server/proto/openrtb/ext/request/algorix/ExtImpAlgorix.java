package org.prebid.server.proto.openrtb.ext.request.algorix;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Algorix Ext Imp
 * @author xunyunbo@algorix.co
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAlgorix {

    @JsonProperty("host")
    String host;

    @JsonProperty("sid")
    String sid;

    @JsonProperty("token")
    String token;
}
