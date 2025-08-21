package org.prebid.server.settings.model;

import lombok.Value;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class StoredDataResult<T> {

    Map<String, T> storedIdToRequest;

    Map<String, T> storedIdToImp;

    List<String> errors;
}
