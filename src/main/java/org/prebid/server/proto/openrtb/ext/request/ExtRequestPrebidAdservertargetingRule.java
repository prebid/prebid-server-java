package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidrequest.ext.prebid.adservertargeting
 */
@Value(staticConstructor = "of")
public class ExtRequestPrebidAdservertargetingRule {

    String key;

    Source source;

    String value;

    public enum Source {
        bidrequest,
        @JsonProperty("static")
        xStatic,
        bidresponse
    }
}
