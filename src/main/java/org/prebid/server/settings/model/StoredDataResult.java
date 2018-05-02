package org.prebid.server.settings.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;
import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class StoredDataResult {

    Map<String, String> storedIdToRequest;

    Map<String, String> storedIdToImp;

    List<String> errors;
}
