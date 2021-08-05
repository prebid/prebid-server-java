package org.prebid.server.proto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class AmpResponse {

    Map<String, JsonNode> targeting;

    ExtResponseDebug debug;

    Map<String, List<ExtBidderError>> errors;

    ExtAmpVideoResponse ext;
}
