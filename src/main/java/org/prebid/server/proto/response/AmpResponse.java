package org.prebid.server.proto.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;

@AllArgsConstructor(staticName = "of")
@Value
public class AmpResponse {

    ObjectNode targeting;

    ExtResponseDebug debug;
}
