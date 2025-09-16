package org.prebid.server.settings.model;

import lombok.Value;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class StoredDataResult {

    Map<String, String> storedIdToRequest;

    Map<String, String> storedIdToImp;

    List<String> errors;
}
