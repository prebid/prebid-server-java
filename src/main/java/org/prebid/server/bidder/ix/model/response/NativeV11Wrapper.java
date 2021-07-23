package org.prebid.server.bidder.ix.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.response.Response;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Native 1.2 to 1.1 tracker compatibility handling
 */
@Value
@AllArgsConstructor(staticName = "of")
public class NativeV11Wrapper {

    @JsonProperty("native")
    Response nativeResponse;
}
