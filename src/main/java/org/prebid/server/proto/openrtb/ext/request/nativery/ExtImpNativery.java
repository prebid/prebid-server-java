package org.prebid.server.proto.openrtb.ext.request.nativery;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpNativery {

    @JsonProperty("widgetId")
    String widgetId;
}
