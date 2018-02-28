package org.prebid.server.settings.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;
import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class StoredRequestResult {

    Map<String, String> storedIdToJson;

    List<String> errors;
}
