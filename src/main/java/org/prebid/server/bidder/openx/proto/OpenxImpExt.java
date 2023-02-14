package org.prebid.server.bidder.openx.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;

import java.util.Map;

/**
 * Defines bidrequest.imp.ext for outgoing requests from OpenX adapter
 */
@Builder(toBuilder = true)
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class OpenxImpExt extends FlexibleExtension {

    @JsonProperty("customParams")
    Map<String, JsonNode> customParams;
}
