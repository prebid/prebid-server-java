package org.prebid.server.bidder.ix.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.response.Response;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Native 1.2 to 1.1 tracker compatibility handling
 */
@Value
@AllArgsConstructor(staticName = "of")
public class NativeWrapper {

    @JsonProperty("native")
    Response nativeResponse;
}
