package org.prebid.server.cookie.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.cookie.model.UidWithExpiry;

import java.util.Map;

@Builder(toBuilder = true)
@Value
public class Uids {

    @JsonProperty("uids")
    Map<String, String> uidsLegacy;

    @JsonProperty("tempUIDs")
    Map<String, UidWithExpiry> uids; // transition to new UIDs format

    Boolean optout;
}
