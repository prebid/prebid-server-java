package org.prebid.server.proto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;

import java.util.List;
import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class AmpResponse {

    Map<String, JsonNode> targeting;

    ExtResponseDebug debug;

    Map<String, List<ExtBidderError>> errors;
}
