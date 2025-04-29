package org.prebid.server.bidder.ix.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.response.Response;
import lombok.Value;

/**
 * Native 1.2 to 1.1 tracker compatibility handling
 */
@Value(staticConstructor = "of")
public class NativeV11Wrapper {

    @JsonProperty("native")
    Response nativeResponse;
}
