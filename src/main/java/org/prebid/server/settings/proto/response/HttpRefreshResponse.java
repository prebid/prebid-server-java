package org.prebid.server.settings.proto.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class HttpRefreshResponse {

    private Map<String, ObjectNode> requests;

    private Map<String, ObjectNode> imps;
}
