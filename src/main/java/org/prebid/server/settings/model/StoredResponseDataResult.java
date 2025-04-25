package org.prebid.server.settings.model;

import lombok.Value;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class StoredResponseDataResult {

    Map<String, String> idToStoredResponses;

    List<String> errors;
}
