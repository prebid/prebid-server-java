package org.prebid.settings.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;
import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public final class StoredRequestResult {

    Map<String, String> storedIdToJson;

    List<String> errors;
}
