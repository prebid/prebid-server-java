package org.prebid.server.settings.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class HttpFetcherResponse {

    private Map<String, ObjectNode> requests;

    private Map<String, ObjectNode> imps;
}
